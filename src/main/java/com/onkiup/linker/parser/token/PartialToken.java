package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TokenGrammar;
import com.onkiup.linker.parser.annotation.AdjustPriority;
import com.onkiup.linker.parser.annotation.MetaToken;
import com.onkiup.linker.parser.annotation.OptionalToken;
import com.onkiup.linker.parser.annotation.SkipIfFollowedBy;
import com.onkiup.linker.parser.util.LoggerLayout;
import com.onkiup.linker.parser.util.ParserError;
import com.onkiup.linker.parser.util.TextUtils;

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
        owner.log("Performing context-aware optionality check for field ${}", field);
        OptionalToken optionalToken = field.getAnnotation(OptionalToken.class);
        boolean result;
        if (optionalToken.whenFieldIsNull().length() != 0) {
          final String fieldName = optionalToken.whenFieldIsNull();
          result = testContextField(owner, fieldName, Objects::isNull);
          owner.log("whenFieldIsNull({}) == {}", fieldName, result);
        } else if (optionalToken.whenFieldNotNull().length() != 0) {
          final String fieldName = optionalToken.whenFieldNotNull();
          result = testContextField(owner, fieldName, Objects::nonNull);
          owner.log("whenFieldNotNull({}) == {}", fieldName, result);
        } else {
          result = optionalToken.whenFollowedBy().length() == 0;
          owner.log("No context-aware conditions found; isOptional = {}", result);
        }
        return result;
      }

      return false;
    } catch (Exception e) {
      throw new ParserError("Failed to determine if field " + field.getName() + " should be optional", owner);
    }
  }

  static boolean testContextField(CompoundToken owner, String fieldName, Predicate<Object> tester)
      throws NoSuchFieldException, IllegalAccessException {
    Field targetField = owner.tokenType().getField(fieldName);
    targetField.setAccessible(true);
    boolean result = tester.test(targetField.get(owner.token()));
    return result;
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

  void dropPopulated();

  boolean isFailed();
  boolean isOptional();

  Optional<CompoundToken<?>> parent();
  Optional<Field> targetField();

  ParserLocation location();
  ParserLocation end();

  void markOptional();
  void onPopulated(ParserLocation end);
  String tag();
  void atEnd();

  default void traceback() {
    onFail();
  }

  List<?> metaTokens();
  void addMetaToken(Object metatoken);

  default boolean isMetaToken() {
    return tokenType().isAnnotationPresent(MetaToken.class);
  }

  /** 
   * @return all characters consumed by the token and its children
   */
  default CharSequence source() {
    PartialToken<?> root = root();
    return ConsumingToken.ConsumptionState.rootBuffer(root)
        .map(buffer -> buffer.subSequence(position(), end().position()))
        .orElse("?!");
  }

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
  default void lookahead(CharSequence source, int from) {
    log("performing lookahead at position {}", from);
    targetField()
      .flatMap(PartialToken::getOptionalCondition)
      .ifPresent(condition -> {
        int start = TextUtils.firstNonIgnoredCharacter(this, source, from);
        CharSequence buffer = source.subSequence(start, start + condition.length());
        log("Loookahead '{}' on '{}'", LoggerLayout.sanitize(condition), LoggerLayout.sanitize(buffer));
        if (!isOptional() && Objects.equals(condition, buffer)) {
          log("Optional condition match: '{}' == '{}'", LoggerLayout.sanitize(condition), LoggerLayout.sanitize(buffer));
          markOptional();
        }
      });

    parent()
        .filter(CompoundToken::onlyOneUnfilledChildLeft)
        .filter(p -> p != this)
        .ifPresent(p -> {
          log("Delegating lookahead to parent {}", p.tag());
          p.lookahead(source, from);
        });
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

  /**
   * @return String containing all characters to ignore for this token
   */
  default String ignoredCharacters() {
    return parent().map(CompoundToken::ignoredCharacters).orElse("");
  }

  default boolean alternativesLeft() {
    return false;
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

  default CharSequence head(int length) {
    return LoggerLayout.head(LoggerLayout.sanitize(source()), 50);
  }

  default LinkedList<PartialToken<?>> path() {
    LinkedList path = parent()
      .map(PartialToken::path)
      .orElseGet(LinkedList::new);
    path.add(this);
    return path;
  }

  default CharSequence dumpTree() {
    return dumpTree(PartialToken::tag);
  }

  default CharSequence dumpTree(Function<PartialToken<?>, CharSequence> formatter) {
    return dumpTree(0, "", "", formatter);
  }

  default CharSequence dumpTree(int offset, CharSequence prefix, CharSequence childPrefix, Function<PartialToken<?>, CharSequence> formatter) {
    return String.format("%s%s\n", prefix, formatter.apply(this));
  }
}

