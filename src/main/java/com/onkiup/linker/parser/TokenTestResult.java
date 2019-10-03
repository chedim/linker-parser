package com.onkiup.linker.parser;

public class TokenTestResult<X> {
  private TestResult result;
  private X token;
  private int length;

  protected TokenTestResult(TestResult result, int length, X token) {
    this.result = result;
    this.length = length;
    this.token = token;
  }

  public TestResult getResult() {
    return result;
  }

  public void setTokenLength(int length) {
    this.length = length;
  }

  public int getTokenLength() {
    return length;
  }

  public X getToken() {
    return token;
  }

  public boolean isFailed() {
    return result == TestResult.FAIL;
  }

  public boolean isMatch() {
    return result == TestResult.MATCH;
  }

  public boolean isContinue() {
    return result == TestResult.CONTINUE;
  }

  public boolean isMatchContinue() {
    return result == TestResult.MATCH_CONTINUE;
  }

  @Override
  public String toString() {
    return "TestResult: " + result + " (" + token + ") ";
  }
}

