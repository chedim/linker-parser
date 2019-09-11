package com.onkiup.linker.parser.token;

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
import com.onkiup.linker.parser.SyntaxError;
import com.onkiup.linker.parser.TokenGrammar;
import com.onkiup.linker.parser.annotation.AdjustPriority;
import com.onkiup.linker.parser.annotation.Alternatives;
import com.onkiup.linker.parser.annotation.IgnoreCharacters;
import com.onkiup.linker.parser.annotation.IgnoreVariant;
import com.onkiup.linker.parser.token.PartialToken;
import com.onkiup.linker.parser.util.ParserError;

public class VariantToken<X extends Rule> implements PartialToken<X> {

  private static final Reflections reflections  = new Reflections(new ConfigurationBuilder()
        .setUrls(ClasspathHelper.forClassLoader(TokenGrammar.class.getClassLoader()))
        .setScanners(new SubTypesScanner(true))
    );

  private Class<X> tokenType;
  private Class<? extends X>[] variants;
  private PartialToken<? extends X> bestShot;
  private int currentVariant;
  private PartialToken<? extends X> token;
  private String ignoreCharacters = "";
  private PartialToken<? extends Rule> parent;
  private boolean rotated = false;
  private ParserLocation location;
  private final Logger logger;

  public VariantToken(PartialToken parent, Class<X> tokenType, ParserLocation location) {
    this.tokenType = tokenType;
    this.logger = LoggerFactory.getLogger(tokenType);
    this.parent = parent;
    this.location = location;
    if (TokenGrammar.isConcrete(tokenType)) {
      throw new IllegalArgumentException("Variant token cannot handle concrete type " + tokenType);
    }

    if (tokenType.isAnnotationPresent(Alternatives.class)) {
      variants = tokenType.getAnnotation(Alternatives.class).value();
    } else {
      final ConcurrentHashMap<Class, Integer> typePriorities = new ConcurrentHashMap<>();
      variants = reflections.getSubTypesOf(tokenType).stream()
        .filter(type -> {
          if (type.isAnnotationPresent(IgnoreVariant.class)) {
            logger.debug("Ignoring variant {} -- marked with @IgnoreVariant", type.getSimpleName());
            return false;
          }
          boolean inTree = findInTree(token -> token != null && token.getTokenType() == type && token.location().position() == location.position()).isPresent();
          if (inTree) {
            logger.debug("Ignoring variant {} -- already in tree with same position ({})", type.getSimpleName(), location.position());
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
            logger.debug("Ignoring " + type + " (extends: " + superClass + ") ");
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
      ignoreCharacters += parent.getIgnoredCharacters();
    }

    if (tokenType.isAnnotationPresent(IgnoreCharacters.class)) {
      ignoreCharacters += tokenType.getAnnotation(IgnoreCharacters.class).value();
    }
  }

  @Override
  public Optional<StringBuilder> pushback(boolean force) {
    StringBuilder result = null;
    if (bestShot == null || token != null && bestShot.end().position() < token.end().position()) {
      bestShot = token;
    }
    if (token != null && token.alternativesLeft() > 0) {
      logger.info("Not pushing parent: token has alternatives: {}", token);
      return token.pullback();
    } else if (currentVariant < variants.length) {
      logger.debug("Not pushing parent back: not all options exhausted for {}", this);
      result = (StringBuilder) (token == null ? null : token.pullback().orElse(null));
      token = null;
    } else {
      logger.debug("{}: Exhausted all variants on pushback, rollbacking parent token {}", this, parent);
      result = (StringBuilder) getParent()
        .flatMap(p -> p.pushback(false))
        .orElse(null);
    }

    return Optional.ofNullable(result);
  }

  @Override
  public void rotate() {
    if (token != null) {
      token.rotate();
      rotated = true;
    }
  }

  @Override
  public void unrotate() {
    if (token != null) {
      token.unrotate();
      rotated = true;
    }
  }

  @Override
  public Optional<StringBuilder> pullback() {
    logger.debug("Pullback request received");
    if (token != null) {
      PartialToken discarded = token;
      StringBuilder result = (StringBuilder) discarded.pullback().orElse(null);
      token = null;
      logger.debug("Pulled back from token: '{]}'", result);
      return Optional.ofNullable(result);
    }
    logger.debug("No token present, nothing to delegate this pullback request to");
    return Optional.empty();
  }

  @Override
  public Optional<PartialToken> advance(boolean forcePopulate) throws SyntaxError {
    if (rotated) {
      logger.debug("Token was rotated, not advancing");
      rotated = false;
      return Optional.of(token);
    } else if (isPopulated()) {
      bestShot = token;
      logger.debug("Populated {} as {}; Advancing to parent", this, token);
      return parent == null ? Optional.empty() : parent.advance(forcePopulate);
    } else if (token != null && token.alternativesLeft() > 0) {
      logger.debug("{}: Advancing to child token with alternatives: {}", this, token);
      return Optional.of(token);
    } else if (currentVariant < variants.length) {
      Class variant = variants[currentVariant++];

      token = PartialToken.forClass(this, variant, location);
      logger.info("Advancing to variant {}/{}: {}", currentVariant, variants.length, token);
      return token.advance(false);
    }

    token = null;

    logger.info("{}: Exhausted all variants on advance, returning to parent {}", this, parent);
    if (parent == null) {
      return Optional.empty();
    }
    
    StringBuilder data = parent.pushback(forcePopulate).orElseGet(StringBuilder::new);
    return Optional.of(new FailedToken(parent, data, location));
  }
  
  @Override
  public boolean isPopulated() {
    return token != null && token.isPopulated();
  }

  @Override
  public X getToken() {
    return token.getToken();
  }

  @Override
  public Class<X> getTokenType() {
    return tokenType;
  }

  public PartialToken resolvedAs() {
    return token;
  }

  @Override
  public Optional<PartialToken> getParent() {
    return Optional.ofNullable(parent);
  }

  @Override
  public String getIgnoredCharacters() {
    return ignoreCharacters;
  }

  private int calculatePriority(Class<? extends X> type) {
    int result = 0;
    if (!TokenGrammar.isConcrete(type)) {
      result += 1000;
    }

    if (findInTree(other -> type == other.getTokenType()).isPresent()) {
      result += 1000;
    }

    if (type.isAnnotationPresent(AdjustPriority.class)) {
      AdjustPriority adjust = type.getAnnotation(AdjustPriority.class);
      result += adjust.value();
      logger.info("Adjusted priority by " + adjust.value()); 
    }

    logger.info(type.getSimpleName() + " priority " + result);

    return result;
  }

  @Override
  public String toString() {
    return "? extends " + tokenType.getName();
  }

  @Override
  public int consumed() {
    if (token == null) {
      return 0;
    }
    return token.consumed();
  }

  @Override
  public int alternativesLeft() {
    return variants.length - currentVariant + (token == null ? 0 : token.alternativesLeft());
  }

  @Override
  public void sortPriorities() {
    token.sortPriorities();
  }

  @Override
  public PartialToken[] getChildren() {
    return new PartialToken[] {token};
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
  public boolean rotatable() {
    return token != null && token.rotatable();
  }

  @Override
  public ParserLocation end() {
    if (token == null) {
      return location;
    }
    return token.end();
  }

  @Override
  public ParserLocation location() {
    if (token != null) {
      return token.location();
    }
    return location;
  }

  @Override
  public StringBuilder source() {
    StringBuilder result = new StringBuilder();
    if (token != null) {
      result.append(token.source());
    }

    return result;
  }

  @Override
  public PartialToken expected() {
    if (bestShot != null) {
      return bestShot.expected();
    }
    return this;
  }
  
  @Override
  public String tag() {
    return "? extends " + tokenType.getSimpleName();
  }
}

