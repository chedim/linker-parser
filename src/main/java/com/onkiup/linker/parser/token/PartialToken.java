package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.SyntaxError;
import com.onkiup.linker.parser.TokenGrammar;
import com.onkiup.linker.parser.annotation.AdjustPriority;

public interface PartialToken<X> {

  static PartialToken forField(PartialToken parent, Field field, int position) {
    Class fieldType = field.getType();
    if (fieldType.isArray()) {
      return new CollectionToken(parent, field, position);
    } else if (Rule.class.isAssignableFrom(fieldType)) {
      if (!TokenGrammar.isConcrete(fieldType)) {
        return new VariantToken(parent, fieldType, position);
      } else {
        return new RuleToken(parent, fieldType, position);
      }
    } else if (fieldType == String.class) {
      return new TerminalToken(parent, field, position);
    }
    throw new IllegalArgumentException("Unsupported field type: " + fieldType);
  }

  static PartialToken forClass(PartialToken parent, Class<? extends Rule> type, int position) {
    if (TokenGrammar.isConcrete(type)) {
      return new RuleToken(parent, type, position);
    } else {
      return new VariantToken(parent, type, position);
    }
  }

  /**
   * advances this token using given subToken and returns next token to populate
   * null sub-token indicates that current token did not match
   * @param subToken populated sub-token
   * @returns next token to populate or null if this is a root token and it has no further tokens to populate
   */
  Optional<PartialToken> advance(boolean forcePopulate) throws SyntaxError;
  
  /**
   * Rollbacks the token until last junction
   * Called by failed child token on a parent
   * @returns StringBuilder with characters to be returned to the buffer or null
   */
  default Optional<StringBuilder> pushback(boolean force) {
    return Optional.empty();
  }

  /**
   * Pullbacks token characters
   * Called by parent token on a child that was failed by latter child
   * @returns StringBuilder with characters to be returned to the buffer of empty
   */
  Optional<StringBuilder> pullback();

  /**
   * @returns Java representation of populated token
   */
  X getToken();

  Class<X> getTokenType();

  boolean isPopulated();

  Optional<PartialToken> getParent();

  default Optional<PartialToken> findInTree(Predicate<PartialToken> comparator) {
    if (comparator.test(this)) {
      return Optional.of(this);
    }

    return getParent()
      .flatMap(parent -> parent.findInTree(comparator));
  }

  default List<PartialToken> getPath() {
    LinkedList<PartialToken> result = new LinkedList<>();
    getParent().ifPresent(parent -> result.addAll(parent.getPath()));
    result.add(this);
    return result;
  }

  /**
   * @returns String containing all characters to ignore for this token
   */
  default String getIgnoredCharacters() {
    return "";
  }

  int position();

  int consumed();

  default int alternativesLeft() {
    return 0;
  }

  default void rotate() {
  }

  default boolean rotatable() {
    return false;
  }

  default void unrotate() {
  }

  default int basePriority() {
    int result = 0;
    Class<X> tokenType = getTokenType();
    if (tokenType.isAnnotationPresent(AdjustPriority.class)) {
      AdjustPriority adjustment = tokenType.getAnnotation(AdjustPriority.class);
      result += adjustment.value();
    }

    for (PartialToken child : getChildren()) {
      if (child != null && child.propagatePriority()) {
        result += child.basePriority();
      }
    }

    return result;
  }

  default boolean propagatePriority() {
    Class<X> tokenType = getTokenType();
    if (tokenType.isAnnotationPresent(AdjustPriority.class)) {
      return tokenType.getAnnotation(AdjustPriority.class).propagate();
    }

    return false;
  }

  default void sortPriorities() {

  }

  default PartialToken[] getChildren() {
    return new PartialToken[0];
  }

  default void setChildren(PartialToken[] children) {
    throw new RuntimeException("setChildren is unsupported for " + this);
  }

  default PartialToken replaceCurrentToken() {
    throw new RuntimeException("Unsupported");
  }

  default void setToken(X token) {
    throw new RuntimeException("Unsupported");  
  }

  default void invalidate() {
    
  }
}

