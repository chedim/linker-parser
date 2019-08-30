package com.onkiup.linker.parser.token;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onkiup.linker.parser.annotation.AdjustPriority;
import com.onkiup.linker.parser.annotation.Alternatives;
import com.onkiup.linker.parser.annotation.IgnoreCharacters;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.SyntaxError;
import com.onkiup.linker.parser.TokenGrammar;
import com.onkiup.linker.parser.token.PartialToken;

public class VariantToken<X extends Rule> implements PartialToken<X> {
  private static final Logger logger = LoggerFactory.getLogger(VariantToken.class);

  private static final Reflections reflections  = new Reflections(new ConfigurationBuilder()
        .setUrls(ClasspathHelper.forClassLoader(TokenGrammar.class.getClassLoader()))
        .setScanners(new SubTypesScanner(true))
    );

  private Class<X> tokenType;
  private Class<? extends X>[] variants;
  private int currentVariant;
  private PartialToken<? extends X> token;
  private String ignoreCharacters = "";
  private PartialToken<? extends Rule> parent;
  private final int position;
  private boolean rotated = false;

  public VariantToken(PartialToken parent, Class<X> tokenType, int position) {
    this.tokenType = tokenType;
    this.parent = parent;
    this.position = position;
    if (TokenGrammar.isConcrete(tokenType)) {
      throw new IllegalArgumentException("Variant token cannot handle concrete type " + tokenType);
    }
    if (tokenType.isAnnotationPresent(Alternatives.class)) {
      variants = tokenType.getAnnotation(Alternatives.class).value();
    } else {
      final ConcurrentHashMap<Class, Integer> typePriorities = new ConcurrentHashMap<>();
      variants = reflections.getSubTypesOf(tokenType).stream()
        .filter(type -> {
          Class superClass = type.getSuperclass();
          if (superClass != tokenType) {
            Class[] interfaces = type.getInterfaces();
            for (Class iface : interfaces) {
              if (iface == tokenType) {
                return true;
              }
            }
            logger.info("Ignoring " + type + " (extends: " + superClass + ") ");
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

          return Integer.compare(typePriorities.get(sub1), typePriorities.get(sub2));
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
    PartialToken<? extends X> oldToken = token;
    token = null;
    if (currentVariant < variants.length) {
      logger.debug("Not pushing parent back: not all options exhausted for {}", this);
      return oldToken.pullback();
    }

    logger.debug("Exhausted all variants, rollbacking parent token");
    StringBuilder result = (StringBuilder) getParent()
      .flatMap(p -> p.pushback(false))
      .orElseGet(StringBuilder::new);
    oldToken.pullback().ifPresent(result::append);
     
    return Optional.of(result);
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
    if (isPopulated()) {
      logger.info("Pulling back resolved token {}", token);
      PartialToken discarded = token;
      token = null;
      return discarded.pullback();
    }
    return Optional.empty();
  }

  @Override
  public Optional<PartialToken> advance(boolean forcePopulate) throws SyntaxError {
    if (rotated) {
      logger.info("Token was rotated, not advancing");
      rotated = false;
      return Optional.of(token);
    } else if (isPopulated()) {
      logger.info("Populated {} as {}; Advancing to parent", this, token);
      return parent == null ? Optional.empty() : parent.advance(forcePopulate);
    } else if (currentVariant < variants.length) {
      Class variant = null;
      PartialToken leftRecursion = null;
      while (currentVariant < variants.length) {
        final Class candidate = variants[currentVariant++];
        leftRecursion = findInTree(token -> token.position() == position && token.getTokenType() == candidate).orElse(null);
        if (leftRecursion != null) {
          logger.debug("Left recursion detected on variant " + candidate.getSimpleName());
        } else {
          variant = candidate;
          break;
        }
      }

      if (leftRecursion == null) {
        token = PartialToken.forClass(this, variant, position);
        logger.info("Advancing to variant {}/{}: {}", currentVariant, variants.length, token);
        return token.advance(false);
      }
    }

    token = null;

    logger.info("Exhausted all variants, returning to parent");
    return parent == null ? Optional.empty() : parent.advance(forcePopulate);
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
    return new StringBuilder("VariantToken[")
      .append(currentVariant)
      .append("/")
      .append(variants.length)
      .append("]")
      .append("@[")
      .append(position)
      .append(" - ")
      .append(position + consumed())
      .append("]")
      .toString();
  }

  @Override
  public int position() {
    return position;
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
    return variants.length - currentVariant - 1;
  }

  @Override
  public int basePriority() {
    return token.basePriority();
  }

  @Override
  public boolean rotatable() {
    return token != null && token.rotatable();
  }
}

