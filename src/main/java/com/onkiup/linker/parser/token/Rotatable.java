package com.onkiup.linker.parser.token;

/**
 * An interface for rotatable ASTs
 */
public interface Rotatable {

  /**
   * @return true if this token's AST can be rotated
   */
  boolean rotatable();

  /**
   * Performs a clockwise rotation on AST represented by this token
   */
  void rotateForth();

  /**
   * Performs a counter clockwise rotation on AST represented by this token
   */
  void rotateBack();
}

