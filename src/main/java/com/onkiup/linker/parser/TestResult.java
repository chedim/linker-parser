package com.onkiup.linker.parser;

public enum TestResult {
  FAIL, CONTINUE, MATCH_CONTINUE, MATCH;

  public static TokenTestResult fail() {
    return FAIL.token(0, null);
  }

  public static TokenTestResult matchContinue(int position, Object token) {
    return MATCH_CONTINUE.token(position, token);
  }

  public static TokenTestResult match(int position, Object token) {
    return MATCH.token(position, token);
  }

  public static TokenTestResult continueNoMatch() {
    return CONTINUE.token(0, null);
  }

  public TokenTestResult token(int length, Object token) {
    return new TokenTestResult(this, length, token);
  }

}

