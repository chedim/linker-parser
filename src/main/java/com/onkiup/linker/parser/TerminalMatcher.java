package com.onkiup.linker.parser;

public class TerminalMatcher implements TokenMatcher {
  
  private final String pattern; 
  private final int patternLen;

  public TerminalMatcher(String pattern) {
    this.pattern = pattern;
    this.patternLen = pattern.length();
  }

  @Override
  public TokenTestResult apply(CharSequence buffer) {
    int bufferLen = buffer.length();
    int charsToCompare = Math.min(patternLen, bufferLen);
    for (int i = 0; i < charsToCompare; i++) {
      if (pattern.charAt(i) != buffer.charAt(i)) {
        return TestResult.fail();
      }
    }

    if (patternLen <= bufferLen) {
      return TestResult.match(patternLen, pattern);
    }
    return TestResult.continueNoMatch();
  }

  @Override
  public String toString() {
    return "TerminalMatcher["+pattern+"]";
  }
}

