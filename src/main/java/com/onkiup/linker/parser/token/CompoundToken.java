package com.onkiup.linker.parser.token;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TokenGrammar;

public interface CompoundToken<X> extends PartialToken<X> {

  static CompoundToken forClass(Class<? extends Rule> type, ParserLocation position) {
    if (position == null) {
      position = new ParserLocation(null, 0, 0, 0);
    }
    if (TokenGrammar.isConcrete(type)) {
      return new RuleToken(null, null, type, position);
    } else {
      return new VariantToken(null, null, type, position);
    }
  }

  void onChildPopulated();

  void onChildFailed();

  int unfilledChildren();

  int currentChild();
  void nextChild(int newIndex);

  /**
   * @return all previously created children, optionally excluding any possible future children
   */
  PartialToken<?>[] children();

  /**
   * @param children an array of PartialToken objects to replace current token's children with 
   */
  void children(PartialToken<?>[] children);

  Optional<PartialToken<?>> nextChild();
  
  /**
   * Walks through token's children in reverse order removing them until the first child with alternativesLeft() > 0
   * If no such child found, then returns full token source
   * @return source for removed tokens
   */
  default Optional<CharSequence> traceback() {
    PartialToken<?>[] children = children();
    if (children.length == 0) {
      invalidate();
      onFail();
      return Optional.empty();
    }
    StringBuilder result = new StringBuilder();
    int newSize =  0;
    for (int i = children.length - 1; i > -1; i--) {
      PartialToken<?> child = children[i];
      if (child == null) {
        continue;
      }
      if (child instanceof CompoundToken) {
        ((CompoundToken)child).traceback().ifPresent(returned -> result.insert(0, returned.toString()));
      } else {
        result.insert(0, child.source().toString());
      }

      child.invalidate();

      if (child.alternativesLeft() > 0) {
        newSize = i + 1;
        break;
      }

      child.onFail();
    }

    if (newSize > 0) {
      PartialToken<?>[] newChildren = new PartialToken<?>[newSize];
      System.arraycopy(children, 0, newChildren, 0, newSize);
      children(newChildren);
      nextChild(newSize - 1);
      log("Traced back to child #{}: {}", newSize, newChildren[newSize-1]);
    } else {
      invalidate();
      onFail();
    }

    return Optional.of(result);
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

