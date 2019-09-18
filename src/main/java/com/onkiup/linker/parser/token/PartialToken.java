package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.SyntaxError;
import com.onkiup.linker.parser.TokenGrammar;
import com.onkiup.linker.parser.annotation.AdjustPriority;
import com.onkiup.linker.parser.annotation.OptionalToken;
import com.onkiup.linker.parser.annotation.SkipIfFollowedBy;
import com.onkiup.linker.parser.util.ParserError;

public interface PartialToken<X> {

  static PartialToken forField(CompoundToken<?> parent, Field field, ParserLocation position) {

    if (position == null) {
      throw new ParserError("Child token position cannot be null", parent);
    }

    Class fieldType = field.getType();
    if (fieldType.isArray()) {
      return new CollectionToken(parent, field, fieldType, position);
    } else if (Rule.class.isAssignableFrom(fieldType)) {
      if (!TokenGrammar.isConcrete(fieldType)) {
        return new VariantToken(parent, field, fieldType, position);
      } else {
        return new RuleToken(parent, field, fieldType, position);
      }
    } else if (fieldType == String.class) {
      return new TerminalToken(parent, field, position);
    }
    throw new IllegalArgumentException("Unsupported field type: " + fieldType);
  }

  static PartialToken forClass(Class<? extends Rule> type, ParserLocation position) {
    if (position == null) {
      position = new ParserLocation(null, 0, 0, 0);
    }
    if (TokenGrammar.isConcrete(type)) {
      return new RuleToken(null, null, type, position);
    } else {
      return new VariantToken(null, null, type, position);
    }
  }

  static CharSequence getOptionalCondition(Field field) {
    if (field == null) {
      return null;
    }
    if (field.isAnnotationPresent(OptionalToken.class)) {
      return field.getAnnotation(OptionalToken.class).whenFollowedBy();
    } else if (field.isAnnotationPresent(SkipIfFollowedBy.class)) {
      return field.getAnnotation(SkipIfFollowedBy.class).value();
    }
    return null;
  }

  static boolean isOptional(Field field) {
    return field != null && field.isAnnotationPresent(OptionalToken.class);
  }

  /**
   * @return Java representation of populated token
   */
  X token();

  Class<X> tokenType();

  boolean isPopulated();
  boolean isFailed();
  boolean isOptional();

  Optional<CompoundToken<?>> parent();
  Optional<Field> targetField();

  ParserLocation location();
  ParserLocation end();

  void markOptional();
  void onPopulated(ParserLocation end); 

  /** 
   * @return all characters consumed by the token and its children
   */
  CharSequence source();

  /**
   * Called upon token failures
   */
  default void onFail() {
    invalidate();
    parent().ifPresent(CompoundToken::onChildFailed);
  }

  /**
   * Called on failed tokens
   * @return true if the token should continue consumption, false otherwise
   */
  default boolean lookahead(CharSequence buffer) {
    CharSequence optionalCondition = PartialToken.getOptionalCondition(targetField().orElse(null));
    final CharSequence[] parentBuffer = new CharSequence[] { buffer };
    boolean conditionPresent = optionalCondition != null && optionalCondition.length() > 0;
    boolean myResult = true;

    if (!isOptional() && conditionPresent) {
      if (buffer.length() >= optionalCondition.length()) {
        CharSequence test = buffer.subSequence(0, optionalCondition.length());
        if (Objects.equals(test, optionalCondition)) {
          parentBuffer[0] = buffer.subSequence(optionalCondition.length(), buffer.length());
          markOptional();
        }
        myResult = false;
      } 
    } else if (conditionPresent) {
      parentBuffer[0] = buffer.subSequence(optionalCondition.length(), buffer.length());
    }

    return myResult || parent()
      // delegating lookahead call to parent
      .flatMap(p -> p.lookahead(parentBuffer[0]) ? Optional.of(true) : Optional.empty())
      .isPresent();
  }

  default Optional<PartialToken<?>> findInTree(Predicate<PartialToken> comparator) {
    if (comparator.test(this)) {
      return Optional.of(this);
    }

    return parent()
      .flatMap(parent -> parent.findInTree(comparator));
  }

  default List<PartialToken<?>> path() {
    LinkedList<PartialToken<?>> result = new LinkedList<>();
    parent().ifPresent(parent -> result.addAll(parent.path()));
    result.add(this);
    return result;
  }

  default int position() {
    ParserLocation location = location();
    if (location == null) {
      return 0;
    }
    return location.position();
  }

  default int basePriority() {
    int result = 0;
    Class<X> tokenType = tokenType();
    if (tokenType.isAnnotationPresent(AdjustPriority.class)) {
      AdjustPriority adjustment = tokenType.getAnnotation(AdjustPriority.class);
      result += adjustment.value();
    }
    return result;
  }

  default boolean propagatePriority() {
    Class<X> tokenType = tokenType();
    if (tokenType.isAnnotationPresent(AdjustPriority.class)) {
      return tokenType.getAnnotation(AdjustPriority.class).propagate();
    }

    return false;
  }

  default void sortPriorities() {

  }

  default PartialToken replaceCurrentToken() {
    throw new RuntimeException("Unsupported");
  }

  default void token(X token) {
    throw new RuntimeException("Unsupported");
  }

  default void invalidate() {
  }

  default void visit(Consumer<PartialToken<?>> visitor) {
    visitor.accept(this);
  }

  default int alternativesLeft() {
    return 0;
  }

  default PartialToken<?> root() {
    PartialToken<?> current = this;
    while(true) {
      PartialToken<?> parent = current.parent().orElse(null);
      if (parent == null) {
        return current;
      }
      current = parent;
    }
  }

  default CharSequence tail(int length) {
    CharSequence source = source();
    if (source.length() > length) {
      source = source.subSequence(source.length() - length, length);
    }
    return String.format("%" + length + "s", source);
  }

}

