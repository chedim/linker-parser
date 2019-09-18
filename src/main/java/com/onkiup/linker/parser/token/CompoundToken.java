package com.onkiup.linker.parser.token;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.onkiup.linker.parser.Rule;

public interface CompoundToken<X extends Rule> extends PartialToken<X> {

  void onChildPopulated();

  void onChildFailed();

  PartialToken<?>[] children();
  void children(PartialToken<?>[] children);

  PartialToken<?> nextChild();
  
  CharSequence traceback();

  /**
   * advances to the next child token or parent
   * @return next token to populate or null if this is a root token and it has no further tokens to populate
   */
  default Optional<PartialToken<?>> advance() {
    if (isPopulated() || isFailed()) {
      return parent().map(p -> p);
    }

    return Optional.of(nextChild());
  }

  /**
   * @return String containing all characters to ignore for this token
   */
  default String ignoredCharacters() {
    return "";
  }

  /**
   * @return number of alternatives for this token, including its children
   */
  @Override
  default int alternativesLeft() {
    return Arrays.stream(children())
      .filter(Objects::nonNull)
      .mapToInt(PartialToken::alternativesLeft)
      .sum();
  }

  @Override
  default int basePriority() {
    int result = PartialToken.super.basePriority();

    for (PartialToken child : children()) {
      if (child != null && child.propagatePriority()) {
        result += child.basePriority();
      }
    }

    return result;
  }

  default void rotate() {
  }

  default boolean rotatable() {
    return false;
  }

  default void unrotate() {
  }

  @Override
  default CharSequence source() {
    StringBuilder result = new StringBuilder();
    Arrays.stream(children())
      .filter(Objects::nonNull)
      .map(PartialToken::source)
      .forEach(result::append);
    return result;
  }

  @Override
  default void visit(Consumer<PartialToken<?>> visitor) {
    Arrays.stream(children())
      .filter(Objects::nonNull)
      .forEach(child -> child.visit(visitor));
    PartialToken.super.visit(visitor);
  }
}

