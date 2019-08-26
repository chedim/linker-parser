package com.onkiup.linker.parser;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

@Deprecated
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
    } catch (InvocationTargetException nfe) {
      Throwable cause = nfe.getCause();
      if (!(cause instanceof NumberFormatException)) {
        return TestResult.fail();
      }
      if (cause.getMessage().indexOf("out of range") > -1){
        return TestResult.fail();
      }
      if (buffer.length() > 1) { 
        // rolling back one character
        try {
          char drop = buffer.charAt(buffer.length() - 1);
          if (drop != '.') {
            Number token = pattern.newInstance(buffer.substring(0, buffer.length() - 1));
            return TestResult.match(buffer.length() - 1, token);
          } 
        } catch (InvocationTargetException nfe2) {
          if (nfe2.getCause() instanceof NumberFormatException) {
            // this is fine
          } else {
            throw new RuntimeException(nfe2.getCause());
          }
        } catch (Throwable e) {
          throw new RuntimeException(e);
        }
      }
    } catch (Throwable e) {
      throw new RuntimeException("Failed to test " + type + " against '" + buffer + "'", e);
    }
    return TestResult.fail();
  }
}
