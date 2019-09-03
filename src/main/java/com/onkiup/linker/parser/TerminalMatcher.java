package com.onkiup.linker.parser;

public class TerminalMatcher implements TokenMatcher {
  
  private final String pattern; 
  private final int patternLen;

  public TerminalMatcher(String pattern) {
    this.pattern = pattern;
    this.patternLen = pattern.length();
  }

  @Override
  public TokenTestResult apply(StringBuilder buffer) {
    int bufferLen = buffer.length();
    int charsToCompare = Math.min(patternLen, bufferLen);
    for (int i = 0; i < charsToCompare; i++) {
      if (pattern.charAt(i) != buffer.charAt(i)) {
        return TestResult.fail();
      }
    }

    if (patternLen <= bufferLen) {
      return TestResult.MATCH.token(patternLen, pattern);
    }
    return TestResult.matchContinue(bufferLen, buffer.toString());
  }

  @Override
  public String toString() {
    return "TerminalMatcher["+pattern+"]";
  }
}

