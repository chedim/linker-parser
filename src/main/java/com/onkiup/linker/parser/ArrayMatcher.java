package com.onkiup.linker.parser;

public class ArrayMatcher implements TokenMatcher {

  private TokenMatcher elementMatcher;

  public ArrayMatcher(TokenMatcher elementMatcher) {
    this.elementMatcher = elementMatcher;
  }

  @Override
  public TokenTestResult apply(StringBuilder stringBuilder) {
    throw new UnsupportedOperationException();
  }
}

