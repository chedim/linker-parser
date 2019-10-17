package com.onkiup.linker.parser.token;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TokenGrammar;
import com.onkiup.linker.parser.annotation.AdjustPriority;
import com.onkiup.linker.parser.annotation.Alternatives;
import com.onkiup.linker.parser.annotation.IgnoreCharacters;
import com.onkiup.linker.parser.annotation.IgnoreVariant;
import com.onkiup.linker.parser.util.ParserError;

/**
 * A PartialToken used to resolve grammar junctions (non-concrete rule classes like interfaces and abstract classes)
 * by iteratively testing each junction variant (concrete non-abstract implementations) until one of them matches parser input
 * This class is crucial to parser's performance as it implements some key optimizations:
 *  -- it prevents parser from ascending to left-recursive tokens before non left-recursive tokens by penalizing the former's priorities
 *  -- it prevents parser from testing left-recursive tokens if same token with same input offset (position) is already in current AST path
 *  -- it dynamically adjusts junction variant priorities during matching based on how frequently junction variants fail or match, making sure that
 *      more frequently matched tokens are tested before more rare tokens
 *  -- it performs basic lexing on parser input by tagging positions in parser buffer as compatible/incompatible. This information becomes crucial
 *      to parser performance by allowing it to skip previously tested and failed grammar paths after following a non-matching grammar "dead end" paths
 *
 *  This class can be additionally optimized by implementing concurrent grammar junction testing
 * @param <X> the grammar junction class to be resolved
 */
public class VariantToken<X extends Rule> extends AbstractToken<X> implements CompoundToken<X>, Serializable {

  private static boolean excludeMatchingParents = true;

  private static final Reflections reflections  = new Reflections(new ConfigurationBuilder()
        .setUrls(ClasspathHelper.forClassLoader(TokenGrammar.class.getClassLoader()))
        .setScanners(new SubTypesScanner(true))
    );

  /**
   * Dynamic priorities registry
   */
  private static final ConcurrentHashMap<Class, Integer> dynPriorities = new ConcurrentHashMap<>();

  private static final ConcurrentHashMap<PartialToken, ConcurrentHashMap<Integer, ConcurrentHashMap<Class, Boolean>>> tags = new ConcurrentHashMap<>();

  private Class<X> tokenType;
  private Class<? extends X>[] variants;
  private transient PartialToken<? extends X>[] values;
  private PartialToken<? extends X> result;
  private transient int nextVariant = 0;
  private String ignoreCharacters = "";
  private transient List<Class<? extends X>> tried = new LinkedList<>();

  public VariantToken(CompoundToken parent, Field field, Class<X> tokenType, ParserLocation location) {
    super(parent, field, location);

    this.tokenType = tokenType;
    if (TokenGrammar.isConcrete(tokenType)) {
      throw new IllegalArgumentException("Variant token cannot handle concrete type " + tokenType);
    }

    if (tokenType.isAnnotationPresent(Alternatives.class)) {
      variants = tokenType.getAnnotation(Alternatives.class).value();
    } else {
      final ConcurrentHashMap<Class, Integer> typePriorities = new ConcurrentHashMap<>();
      variants = (reflections.getSubTypesOf(tokenType).stream()
          .filter(TokenGrammar::isConcrete)
        .filter(type -> {
          if (type.isAnnotationPresent(IgnoreVariant.class)) {
            log("Ignoring variant {} -- marked with @IgnoreVariant", type.getSimpleName());
            return false;
          }
          if (isLeftRecursive(type)) {
            log("Ignoring variant {} -- left recursive", type.getSimpleName());
            return false;
          }
          if (excludeMatchingParents) {
            boolean inTree = findInPath(token -> token != null && token.tokenType() == type &&
                token.location().position() == location.position()).isPresent();
            if (inTree) {
              log("Ignoring variant {} -- already in tree with same position ({})", type.getSimpleName(),
                  location.position());
              return false;
            }
          }

          Boolean tagged = getTag(type).orElse(null);
          if (tagged != null && !tagged) {
            log("Ignoring " + type + " (tagged as failed for this position)");
            return false;
          }
          return true;
        })
        .sorted((sub1, sub2) -> {
          if (!typePriorities.containsKey(sub1)) {
            typePriorities.put(sub1, calculatePriority(sub1));
          }
          if (!typePriorities.containsKey(sub2)) {
            typePriorities.put(sub2, calculatePriority(sub2));
          }

          int result = Integer.compare(typePriorities.get(sub1), typePriorities.get(sub2));
          if (result == 0) {
            result = sub1.getName().compareTo(sub2.getName());
          }
          return result;
        })
        .toArray(Class[]::new));
    }
    values = new PartialToken[variants.length];

    if (parent != null) {
      ignoreCharacters = parent.ignoredCharacters();
    }

    if (tokenType.isAnnotationPresent(IgnoreCharacters.class)) {
      ignoreCharacters += tokenType.getAnnotation(IgnoreCharacters.class).value();
    }
  }

  private boolean isLeftRecursive(Class<? extends X> target) {
    return parent().map(p -> p.tokenType() == target && p.position() == position()).orElse(false);
  }

  private boolean willLeftRecurse(Class<? extends X> target) {
    Field[] fields = target.getDeclaredFields();
    return fields.length > 0 && tokenType.isAssignableFrom(fields[0].getType());
  }

  @Override
  public Optional<PartialToken<?>> nextChild() {
    if (nextVariant >= variants.length) {
      log("Unable to return next child: variants exhausted (nextVariant = {}, variants total = {})", nextVariant, variants.length);
      onFail();
      return Optional.empty();
    }
    Boolean tag = getTag(variants[nextVariant]).orElse(null);
    while (Boolean.FALSE.equals(tag) && ++nextVariant < variants.length) {
      log("Skipping variant {} -- tagged as failed for position {}", variants[nextVariant], position());
      tag = getTag(variants[nextVariant]).orElse(null);
    }

    if (nextVariant >= variants.length) {
      onFail();
      return Optional.empty();
    }

    if (values[nextVariant] == null || values[nextVariant].isFailed() || values[nextVariant].isPopulated()) {
      log("Creating partial token for nextChild#{}", nextVariant);
      updateDynPriority(variants[nextVariant], 10);
      tried.add(variants[nextVariant]);
      values[nextVariant] = PartialToken.forField(this, targetField().orElse(null), variants[nextVariant], location());
    }

    log("nextChild#{} = {}", nextVariant, values[nextVariant].tag());
    return Optional.of(values[nextVariant++]);
  }

  @Override
  public PartialToken<?>[] children() {
    if (nextVariant >= values.length) {
      return new PartialToken[0];
    }
    return new PartialToken[] {values[currentChild()]};
  }

  @Override
  public void children(PartialToken<?>[] children) {
    throw new RuntimeException("Unable to set children on VariantToken");
  }

  @Override
  public void onChildPopulated() {
    int current = currentChild();
    updateDynPriority(variants[current], -20);
    if (values[current] == null) {
      throw new ParserError("No current token but onChildToken was called...", this);
    }
    if (TokenGrammar.isConcrete(variants[current])) {
      storeTag(values[current],true);
    }
    if (values[current].isMetaToken()) {
      log("Metatoken detected");
      addMetaToken(values[current].token());
      location(values[current].end());
      values[current] = null;
      nextVariant = 0;
      return;
    }
    onPopulated(values[current].end());
  }

  @Override
  public void onPopulated(ParserLocation end) {
    super.onPopulated(end);
    result = values[currentChild()];
  }

  private void storeTag(PartialToken token, boolean result) {
    PartialToken<?> root = root();
    int position = token.position();
    Class ofType = token.tokenType();
    if (!tags.containsKey(root)) {
      tags.put(root, new ConcurrentHashMap<>());
    }
    ConcurrentHashMap<Integer, ConcurrentHashMap<Class, Boolean>> myTags = tags.get(root);
    if (!myTags.containsKey(position)) {
      myTags.put(position, new ConcurrentHashMap<>());
    }
    if (result || !myTags.get(position).containsKey(ofType)) {
      myTags.get(position).put(ofType, result);
      log("Tagged position {} as {} with type {}", position, result ? "compatible" : "incompatible", ofType.getName());
    }
  }

  private <Z> Optional<Boolean> getTag(Class<Z> forType) {
    log("Searching for tags on {}", forType.getName());
    return getTags().map(tags -> tags.get(forType));
  }

  private Optional<Map<Class, Boolean>> getTags() {
    int position = location().position();
    PartialToken<?> root = root();
    log("Searching tags for position {}", position);
    if (!tags.containsKey(root)) {
      log("Did not find tags for root token");
      return Optional.empty();
    }
    return Optional.ofNullable(tags.get(root).get(position));
  }

  @Override
  public void onChildFailed() {
    int current = currentChild();
    updateDynPriority(variants[current], 30);
    if (TokenGrammar.isConcrete(variants[current])) {
      storeTag(values[current],false);
    }
    if (nextVariant >= variants.length) {
      onFail();
    } else {
      dropPopulated();
    }
  }

  @Override
  public Optional<X> token() {
    if (result != null) {
      return (Optional<X>)result.token();
    }
    int current = currentChild();
    if (values[current] == null) {
      return Optional.empty();
    }
    return (Optional<X>) values[current].token();
  }

  @Override
  public Class<X> tokenType() {
    return tokenType;
  }

  @Override
  public void onFail() {
    log("Tried: {}", tried.stream().map(Class::getSimpleName).collect(Collectors.joining(", ")));
    result = null;
    super.onFail();
  }

  @Override
  public void traceback() {
    log("!!! TRACING BACK");
    if (variants.length == 0) {
      onFail();
      return;
    }
    int current = currentChild();
    for (int i = currentChild(); i > -1; i--) {
      PartialToken<?> token = values[i];
      if (token != null) {
        token.traceback();
        dropPopulated();
        if (token.alternativesLeft()){
          nextVariant = i;
          break;
        }
      }
      nextVariant = i;
    }

    if (nextVariant == 0) {
      nextVariant = current + 1;
    }

    if (nextVariant >= variants.length) {
      onFail();
      return;
    }
    log("Traced back fro variant#{} to variant#{}: {}", current, nextVariant, values[nextVariant]);
  }

  private int calculatePriority(Class<? extends X> type) {
    int result = dynPriorities.getOrDefault(type, 0);
    if (!TokenGrammar.isConcrete(type)) {
      result += 1000;
    }

    if (findInPath(other -> type == other.tokenType()).isPresent()) {
      result += 1000;
    }

    if (willLeftRecurse(type)) {
      result += 99999;
    }

    if (type.isAnnotationPresent(AdjustPriority.class)) {
      AdjustPriority adjust = type.getAnnotation(AdjustPriority.class);
      result += adjust.value();
      log("Adjusted priority by " + adjust.value());
    }

    log(type.getSimpleName() + " priority " + result);

    return result;
  }

  @Override
  public String tag() {
    return "? extends " + tokenType.getName() + "(" + position() + ")";
  }

  @Override
  public ParserLocation end() {
    int current = currentChild();
    return isFailed() || values[current] == null ? location() : values[current].end();
  }

  @Override
  public void atEnd() {
    int current = currentChild();
    if (values[current] == null) {
      onFail();
    }
    values[current].atEnd();
    if (values[current].isPopulated()) {
      onPopulated(values[current].end());
    } else {
      onFail();
    }
  }

  @Override
  public String toString() {
    ParserLocation location = location();
    return String.format(
        "%50.50s || %s (%d/%d) (%d:%d -- %d - %d)",
        head(50),
        tag(),
        nextVariant,
        variants.length,
        location.line(),
        location.column(),
        location.position(),
        end().position()
    );
  }

  @Override
  public boolean alternativesLeft() {
    if (isFailed() || variants.length == 0) {
      log("failed -- no alternatives");
      return false;
    }
    if (nextVariant < variants.length) {
      log("some untested variants left -- counting as alternatives");
      return true;
    }
    for (int i = currentChild(); i > -1; i--) {
      if (values[i] != null) {
        if (values[i].alternativesLeft()) {
          log("found alternatives at value#{}: {}", i, values[i]);
          return true;
        }
      } else {
        log("value#{} is null -- counting as an alternative", i);
        return true;
      }
    }
    log("-- no alternatives left in any of {} variants", variants.length);
    return false;
  }

  @Override
  public void sortPriorities() {
    int current = currentChild();
    if (values[current] != null) {
      values[currentChild()].sortPriorities();
    }
  }

  @Override
  public boolean propagatePriority() {
    if (tokenType.isAnnotationPresent(AdjustPriority.class)) {
      return tokenType.getAnnotation(AdjustPriority.class).propagate();
    }
    int current = currentChild();
    if (values[current] != null) {
      return values[current].propagatePriority();
    }
    return false;
  }

  @Override
  public int basePriority() {
    int result = 0;
    if (tokenType.isAnnotationPresent(AdjustPriority.class)) {
      result += tokenType.getAnnotation(AdjustPriority.class).value();
    }
    int current = currentChild();
    if (values[current].propagatePriority()) {
      result += values[current].basePriority();
    }
    return result;
  }

  public Optional<PartialToken<? extends X>> resolvedAs() {
    return Optional.ofNullable(values[currentChild()]);
  }

  @Override
  public int unfilledChildren() {
    return variants.length - currentChild();
  }

  @Override
  public int currentChild() {
    return nextVariant == 0 ? 0 : nextVariant - 1;
  }

  @Override
  public void nextChild(int newIndex) {
    throw new UnsupportedOperationException();
  }

  private static void updateDynPriority(Class target, int change) {
    if (!dynPriorities.containsKey(target)) {
      dynPriorities.put(target, 0);
    }
    dynPriorities.put(target, dynPriorities.get(target) + change);
  }

  @Override
  public CharSequence dumpTree(int offset, CharSequence prefix, CharSequence childPrefix, Function<PartialToken<?>, CharSequence> formatter) {
    final int childOffset = offset + 1;
    String insideFormat = "%s ├─%s %s: %s";
    String lastFormat = "%s └─%s %s: %s";
    StringBuilder result = new StringBuilder(super.dumpTree(offset, prefix, childPrefix, formatter));
    for (int i = 0; i <= nextVariant; i++) {
      if (i < variants.length) {
        boolean last = i == variants.length - 1 || i == nextVariant || (values[i+1] == null && i == nextVariant - 1);
        String format = last ? lastFormat : insideFormat;
        PartialToken<? extends X> child = values[i];
        String variantName = variants[i].getSimpleName();
        if (child == null && !isPopulated()) {
          if (i < nextVariant) {
            result.append(String.format(format, childPrefix, "", variantName, null));
            result.append('\n');
          } else {
            continue;
          }
        } else if (child != null && child.isFailed() && !isPopulated()) {
          result.append(child.dumpTree(childOffset, String.format(format, childPrefix, "[F]", variantName, ""), childPrefix + (last ? "  " : " │"),  formatter));
        } else if (child != null &&  child.isPopulated()) {
          result.append(child.dumpTree(childOffset, String.format(format, childPrefix, "[+]", variantName, ""), childPrefix + (last ? "  " : " │"),  formatter));
        } else if (child != null) {
          result.append(child.dumpTree(childOffset, String.format(format, childPrefix, ">>>", variantName, ""), childPrefix + (last ? "  " : " │"),  formatter));
        }
      }
    }
    return result;
  }
}

