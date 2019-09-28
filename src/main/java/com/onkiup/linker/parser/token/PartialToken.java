package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.slf4j.Logger;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TokenGrammar;
import com.onkiup.linker.parser.annotation.AdjustPriority;
import com.onkiup.linker.parser.annotation.OptionalToken;
import com.onkiup.linker.parser.annotation.SkipIfFollowedBy;
import com.onkiup.linker.parser.util.LoggerLayout;
import com.onkiup.linker.parser.util.ParserError;

public interface PartialToken<X> {

  static PartialToken forField(CompoundToken parent, Field field, ParserLocation position) {

    if (position == null) {
      throw new ParserError("Child token position cannot be null", parent);
    }

    Class fieldType = field.getType();
    return forField(parent, field, fieldType, position);
  }

  static <X> PartialToken<X> forField(CompoundToken parent, Field field, Class fieldType, ParserLocation position) {
    if (fieldType.isArray()) {
      return new CollectionToken(parent, field, fieldType, position);
    } else if (Rule.class.isAssignableFrom(fieldType)) {
      if (!TokenGrammar.isConcrete(fieldType)) {
        return new VariantToken(parent, field, fieldType, position);
      } else {
        return new RuleToken(parent, field, fieldType, position);
      }
    } else if (fieldType == String.class) {
      return (PartialToken<X>) new TerminalToken(parent, field, fieldType, position);
    } else if (fieldType.isEnum()) {
      return (PartialToken<X>) new EnumToken(parent, field, fieldType, position);
    }
    throw new IllegalArgumentException("Unsupported field type: " + fieldType);
  }

  static Optional<CharSequence> getOptionalCondition(Field field) {
    if (field == null) {
      return Optional.empty();
    }
    CharSequence result = null;
    if (field.isAnnotationPresent(OptionalToken.class)) {
      result = field.getAnnotation(OptionalToken.class).whenFollowedBy();
    } else if (field.isAnnotationPresent(SkipIfFollowedBy.class)) {
      result = field.getAnnotation(SkipIfFollowedBy.class).value();
    }

    return Optional.ofNullable(result == null || result.length() == 0 ? null : result);
  }

  static boolean hasOptionalAnnotation(Field field) {
    return field != null && (field.isAnnotationPresent(OptionalToken.class) || field.isAnnotationPresent(SkipIfFollowedBy.class));
  }

  /**
   * Context-aware field optionality checks
   * @param owner Context to check
   * @param field Field to check
   * @return true if the field should be optional in this context
   */
  static boolean isOptional(CompoundToken owner, Field field) {
    try {
      if (field.isAnnotationPresent(OptionalToken.class)) {
        OptionalToken optionalToken = field.getAnnotation(OptionalToken.class);
        if (optionalToken.whenFieldIsNull().length() != 0) {
          final String fieldName = optionalToken.whenFieldIsNull();
          Field targetField = owner.tokenType().getField(fieldName);
          targetField.setAccessible(true);
          return targetField.get(owner.token()) == null;
        }

        return optionalToken.whenFollowedBy().length() != 0;
      }

      return false;
    } catch (Exception e) {
      throw new ParserError("Failed to determine if field " + field.getName() + " should be optional", owner);
    }
  }

  /**
   * @return Java representation of populated token
   */
  Optional<X> token();

  Class<X> tokenType();

  /**
   * Called by parser to detect if this token is populated
   * The result of this method should always be calculated
   */
  boolean isPopulated();
  boolean isFailed();
  boolean isOptional();

  Optional<CompoundToken<?>> parent();
  Optional<Field> targetField();

  ParserLocation location();
  ParserLocation end();

  void markOptional();
  void onPopulated(ParserLocation end);
  String tag();

  Optional<CharSequence> traceback();

  /** 
   * @return all characters consumed by the token and its children
   */
  CharSequence source();

  Logger logger();

  default void log(CharSequence message, Object... arguments) {
    logger().debug(message.toString(), arguments);
  }

  default void error(CharSequence message, Throwable error) {
    logger().error(message.toString(), error);
  }


  /**
   * Called upon token failures
   */
  default void onFail() {
    log("!!! FAILED !!!");
    invalidate();
  }

  /**
   * Called on failed tokens
   * @return true if the token should continue consumption, false otherwise
   */
  default boolean lookahead(CharSequence buffer) {
    log("performing lookahead");
    return targetField()
      .flatMap(PartialToken::getOptionalCondition)
      .map(condition -> {
        log("Loookahead '{}' on '{}'", condition, buffer);
        final CharSequence[] parentBuffer = new CharSequence[] { buffer };
        boolean myResult = true;
        if (!isOptional()) {
          if (buffer.length() >= condition.length()) {
            CharSequence test = buffer.subSequence(0, condition.length());
            if (Objects.equals(test, condition)) {
              log("Optional condition match: '{}' == '{}'", condition, test);
              parentBuffer[0] = buffer.subSequence(condition.length(), buffer.length());
              markOptional();
            }
            myResult = false;
          } else if (!condition.subSequence(0, buffer.length()).equals(buffer)) {
            parentBuffer[0] = buffer;
            myResult = false;
          }
        } else {
          parentBuffer[0] = buffer.subSequence(condition.length(), buffer.length());
        }

        return myResult || isOptional() && parent()
            .filter(p -> p.unfilledChildren() == 1)
            .filter(p -> p.lookahead(parentBuffer[0]))
            .isPresent();
      }).orElseGet(() -> targetField()
            .flatMap(field -> parent().map(parent -> isOptional(parent, field)))
            .orElse(false)
        );
  }

  default Optional<PartialToken<?>> findInTree(Predicate<PartialToken> comparator) {
    if (comparator.test(this)) {
      return Optional.of(this);
    }

    return parent()
      .flatMap(parent -> parent.findInTree(comparator));
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
    return LoggerLayout.ralign(LoggerLayout.sanitize(source().toString()), length);
  }

  default LinkedList<PartialToken> path() {
    LinkedList path = parent()
      .map(PartialToken::path)
      .orElseGet(LinkedList::new);
    path.add(this);
    return path;
  }
}

