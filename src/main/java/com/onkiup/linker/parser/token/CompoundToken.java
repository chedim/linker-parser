package com.onkiup.linker.parser.token;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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
  default boolean hasUnfilledChildren() {
    return unfilledChildren() > 0;
  }

  default boolean onlyOneUnfilledChildLeft() {
    return unfilledChildren() == 1;
  }

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
  default void traceback() {
    log("!!! TRACING BACK");
    PartialToken<?>[] children = children();
    if (children.length == 0) {
      invalidate();
      onFail();
      return;
    }
    int newSize =  0;
    for (int i = children.length - 1; i > -1; i--) {
      PartialToken<?> child = children[i];
      if (child == null) {
        continue;
      }

      child.traceback();

      if (!child.isFailed()) {
        log("found alternatives at child#{}", i);
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
      dropPopulated();
      log("Traced back to child #{}: {}", newSize - 1, newChildren[newSize-1].tag());
    } else {
      onFail();
    }
  }

  /**
   * @return number of alternatives for this token, including its children
   */
  @Override
  default boolean alternativesLeft() {
    PartialToken<?>[] children = children();
    for (int i = 0; i < children.length; i++) {
      PartialToken<?> child = children[i];
      if (child != null) {
        log("getting alternatives from child#{} {}", i, child.tag());
        if (child.alternativesLeft()) {
          log("child#{} {} reported that it has alternatives", i, child.tag());
          return true;
        }
      }
    }
    return false;
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
  default void visit(Consumer<PartialToken<?>> visitor) {
    Arrays.stream(children())
      .filter(Objects::nonNull)
      .forEach(child -> child.visit(visitor));
    PartialToken.super.visit(visitor);
  }
}

