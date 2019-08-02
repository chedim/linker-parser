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
    if (pattern.startsWith(buffer.toString())) {
      if (patternLen == bufferLen) {
        return TestResult.MATCH.token(patternLen, pattern);
      }
      return TestResult.matchContinue(bufferLen, buffer.toString());
    }
    return TestResult.fail();
  }
}

