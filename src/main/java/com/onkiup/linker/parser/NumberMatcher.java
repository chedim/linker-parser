package com.onkiup.linker.parser;

import java.lang.reflect.Constructor;

public class NumberMatcher implements TokenMatcher {
  private Constructor<? extends Number> pattern;
  private Class<? extends Number> type;

  public NumberMatcher(Class<? extends Number> type) {
    try {
      this.type = type;
      this.pattern = type.getConstructor(String.class);
    } catch (NoSuchMethodException nse) {
      throw new RuntimeException("Failed to create number matcher for type '" + type.getCanonicalName() + "'", nse);
    }
  }

  @Override
  public TokenTestResult apply(StringBuilder buffer) {
    try {
      pattern.newInstance(buffer.toString());
      return TestResult.matchContinue(buffer.length(), buffer.toString());
    } catch (NumberFormatException nfe) {
      if (buffer.length() > 1) { 
        // rolling back one character
        try {
          Number token = pattern.newInstance(buffer.substring(buffer.length() - 1));
          return TestResult.match(buffer.length() - 1, token);
        } catch (NumberFormatException nfe2) {
          // this is fine
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to test " + type + " against '" + buffer + "'", e);
    }
    return TestResult.fail();
  }
}
