package com.onkiup.linker.parser.token;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TokenGrammar;

/**
 * Common interface for any tokens that can contain children tokens
 * @param <X> the type of resulting token
 */
public interface CompoundToken<X> extends PartialToken<X>, Serializable {

  /**
   * Creates a new CompoundToken for the provided class
   * @param type class for which new token should be created
   * @param position position at which the token will be located in the parser's input
   * @return created CompoundToken
   */
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

  /**
   * Callback method invoked every time a child token is successfully populated from parser's input
   */
  void onChildPopulated();

  /**
   * Callback method invoked every time a child token population fails
   */
  void onChildFailed();

  /**
   * @return the number of children left to be filled
   */
  int unfilledChildren();

  /**
   * @return true if token contains any unfilled children
   */
  default boolean hasUnfilledChildren() {
    return unfilledChildren() > 0;
  }

  /**
   * @return true when this token has only one unfilled child left
   */
  default boolean onlyOneUnfilledChildLeft() {
    return unfilledChildren() == 1;
  }

  /**
   * @return the number of currently populating child
   */
  int currentChild();

  /**
   * Forces the token to move its internal children pointer so that next populating child will be from the provided position
   * @param newIndex the position of the child to be populated next
   */
  void nextChild(int newIndex);

  /**
   * @return all previously created children, optionally excluding any possible future children
   */
  PartialToken<?>[] children();

  /**
   * @param children an array of PartialToken objects to replace current token's children with
   */
  void children(PartialToken<?>[] children);

  /**
   * @return the next child of this token to be populated
   */
  Optional<PartialToken<?>> nextChild();

  /**
   * Walks through token's children in reverse order removing them until the first child with alternativesLeft() &gt; 0
   * If no such child found, then returns full token source
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

  /**
   * Rotates this token
   */
  default void rotate() {
  }

  /**
   * @return true when this token can be rotated
   */
  default boolean rotatable() {
    return false;
  }

  /**
   * Performs reverse-rotation on this token
   */
  default void unrotate() {
  }

  /**
   * Uses the given visitor to walk over the AST starting with this token
   * @param visitor token visitor
   */
  @Override
  default void visit(Consumer<PartialToken<?>> visitor) {
    Arrays.stream(children())
      .filter(Objects::nonNull)
      .forEach(child -> child.visit(visitor));
    PartialToken.super.visit(visitor);
  }
}

