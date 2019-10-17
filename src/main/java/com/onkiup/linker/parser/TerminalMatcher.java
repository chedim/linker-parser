package com.onkiup.linker.parser;

public class TerminalMatcher implements TokenMatcher {
  
  private final String pattern; 
  private final int patternLen;
  private final boolean ignoreCase;

  public TerminalMatcher(String pattern, boolean ignoreCase) {
    this.pattern = pattern;
    this.patternLen = pattern.length();
    this.ignoreCase = ignoreCase;
  }

  @Override
  public TokenTestResult apply(CharSequence buffer) {
    int bufferLen = buffer.length();
    int charsToCompare = Math.min(patternLen, bufferLen);
    for (int i = 0; i < charsToCompare; i++) {
      char patternChar = ignoreCase ? Character.toLowerCase(pattern.charAt(i)) : pattern.charAt(i);
      char bufferChar = ignoreCase ? Character.toLowerCase(buffer.charAt(i)) : buffer.charAt(i);
      if (patternChar != bufferChar) {
        return TestResult.fail();
      }
    }

    if (patternLen <= bufferLen) {
      return TestResult.match(patternLen, buffer.subSequence(0, patternLen));
    }
    return TestResult.continueNoMatch();
  }

  @Override
  public String toString() {
    return "TerminalMatcher["+pattern+"]";
  }
}

