package com.onkiup.linker.parser;

public class NullMatcher implements TokenMatcher {

  public NullMatcher() {

  }

  @Override
  public TokenTestResult apply(CharSequence buffer) {
    return TestResult.match(0, null);
  }

  @Override
  public String toString() {
    return "NullMatcher";
  }
}

