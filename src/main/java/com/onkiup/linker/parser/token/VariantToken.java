package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TokenGrammar;
import com.onkiup.linker.parser.annotation.AdjustPriority;
import com.onkiup.linker.parser.annotation.Alternatives;
import com.onkiup.linker.parser.annotation.IgnoreCharacters;
import com.onkiup.linker.parser.annotation.IgnoreVariant;
import com.onkiup.linker.parser.util.LoggerLayout;

public class VariantToken<X extends Rule> extends AbstractToken<X> implements CompoundToken<X> {

  private static final Reflections reflections  = new Reflections(new ConfigurationBuilder()
        .setUrls(ClasspathHelper.forClassLoader(TokenGrammar.class.getClassLoader()))
        .setScanners(new SubTypesScanner(true))
    );

  private static final ConcurrentHashMap<Class, Integer> dynPriorities = new ConcurrentHashMap<>();

  private Class<X> tokenType;
  private Class<? extends X>[] variants;
  private int nextVariant = 0;
  private PartialToken<? extends X> token;
  private String ignoreCharacters = "";

  public VariantToken(CompoundToken parent, Field field, Class<X> tokenType, ParserLocation location) {
    super(parent, field, location);

    this.tokenType = tokenType;
    if (TokenGrammar.isConcrete(tokenType)) {
      throw new IllegalArgumentException("Variant token cannot handle concrete type " + tokenType);
    }

    if (findInTree(token -> token != this && token.tokenType() == tokenType && token.position() == position()).isPresent()) {
      log("---||| Not descending: already selecting same Variant sub-type at this location");
      variants = new Class[0];
      onFail();
      return;
    }

    if (tokenType.isAnnotationPresent(Alternatives.class)) {
      variants = tokenType.getAnnotation(Alternatives.class).value();
    } else {
      final ConcurrentHashMap<Class, Integer> typePriorities = new ConcurrentHashMap<>();
      variants = reflections.getSubTypesOf(tokenType).stream()
        .filter(type -> {
          if (type.isAnnotationPresent(IgnoreVariant.class)) {
            log("Ignoring variant {} -- marked with @IgnoreVariant", type.getSimpleName());
            return false;
          }
          boolean inTree = findInTree(token -> token != null && token.tokenType() == type && token.location().position() == location.position()).isPresent();
          if (inTree) {
            log("Ignoring variant {} -- already in tree with same position ({})", type.getSimpleName(), location.position());
            return false;
          }
          Class superClass = type.getSuperclass();
          if (superClass != tokenType) {
            Class[] interfaces = type.getInterfaces();
            for (Class iface : interfaces) {
              if (iface == tokenType) {
                return true;
              }
            }
            log("Ignoring " + type + " (extends: " + superClass + ") ");
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
        .toArray(Class[]::new);
    }

    if (parent != null) {
      ignoreCharacters = parent.ignoredCharacters();
    }

    if (tokenType.isAnnotationPresent(IgnoreCharacters.class)) {
      ignoreCharacters += tokenType.getAnnotation(IgnoreCharacters.class).value();
    }
  }

  @Override
  public Optional<PartialToken<?>> nextChild() {
    if (nextVariant >= variants.length) {
      return Optional.empty();
    }
    return Optional.of(token = PartialToken.forField(this, targetField().orElse(null), variants[nextVariant++], location()));
  }

  @Override
  public PartialToken<?>[] children() {
    return new PartialToken<?>[] {token};
  }

  @Override
  public void children(PartialToken<?>[] children) {
    throw new RuntimeException("Unable to set children on VariantToken");
  }

  @Override
  public void onChildPopulated() {
    updateDynPriority(variants[nextVariant - 1], -10);
    onPopulated(token.end());
  }

  @Override
  public void onChildFailed() {
    updateDynPriority(variants[nextVariant - 1], 10);
    if (nextVariant >= variants.length) {
      onFail();
    } else {
      dropPopulated();
    }
  }

  @Override
  public Optional<X> token() {
    return (Optional<X>) token.token();
  }

  @Override
  public Class<X> tokenType() {
    return tokenType;
  }

  @Override
  public Optional<CharSequence> traceback() {
    Optional<CharSequence> result = null;
    if (token == null) {
      return Optional.empty();
    } else if (token instanceof CompoundToken) {
      result = ((CompoundToken<? extends X>)token).traceback();
      dropPopulated();
    } else {
      result = Optional.of(token.source());
      dropPopulated();
    }
    token = null;
    return result;
  }

  private int calculatePriority(Class<? extends X> type) {
    int result = dynPriorities.getOrDefault(type, 0);
    if (!TokenGrammar.isConcrete(type)) {
      result += 1000;
    }

    if (findInTree(other -> type == other.tokenType()).isPresent()) {
      result += 1000;
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
    return "? extends " + tokenType.getName();
  }

  @Override
  public String toString() {
    return String.format("%-50.50s || %s (%d/%d) (position: %d)", tail(50),  tag(), nextVariant, variants.length, position());
  }

  @Override
  public int alternativesLeft() {
    return variants.length - nextVariant + (token == null ? 0 : token.alternativesLeft());
  }

  @Override
  public void sortPriorities() {
    token.sortPriorities();
  }

  @Override
  public boolean propagatePriority() {
    if (tokenType.isAnnotationPresent(AdjustPriority.class)) {
      return tokenType.getAnnotation(AdjustPriority.class).propagate();
    }
    return token.propagatePriority();
  }

  @Override
  public int basePriority() {
    int result = 0;
    if (tokenType.isAnnotationPresent(AdjustPriority.class)) {
      result += tokenType.getAnnotation(AdjustPriority.class).value();
    }
    if (token.propagatePriority()) {
      result += token.basePriority();
    }
    return result;
  }

  @Override
  public StringBuilder source() {
    StringBuilder result = new StringBuilder();
    if (token != null) {
      result.append(token.source());
    }

    return result;
  }

  public Optional<PartialToken<? extends X>> resolvedAs() {
    return Optional.ofNullable(token);
  }
  
  @Override
  public int unfilledChildren() {
    return (token != null && token.isPopulated()) || variants.length == 0 ? 0 : 1;
  }

  @Override
  public int currentChild() {
    return nextVariant - 1;
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
}

