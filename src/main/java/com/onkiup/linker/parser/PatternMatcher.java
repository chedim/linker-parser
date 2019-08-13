package com.onkiup.linker.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternMatcher implements TokenMatcher {
  private final Pattern pattern;
  private final String replacement;
  private final String until;
  private final Matcher matcher;

  public PatternMatcher(CapturePattern pattern) {
    String matcherPattern = pattern.pattern();
    if (matcherPattern.length() == 0) {
      if (pattern.until().length() == 0) {
        throw new IllegalArgumentException("Either pattern or until must be specified");
      } else {
        matcherPattern = pattern.until();
      }
    }
    this.pattern = Pattern.compile(matcherPattern);
    this.replacement = pattern.replacement();
    this.until = pattern.until();
    matcher = this.pattern.matcher("");
  }

  @Override
  public TokenTestResult apply(StringBuilder buffer) {
    matcher.reset(buffer);
    boolean matches = matcher.matches(), 
            lookingAt = matcher.lookingAt(), 
            hitEnd = matcher.hitEnd();

    if (until.length() == 0) {
      if(hitEnd && lookingAt && matches) {
        return TestResult.matchContinue(buffer.length(), buffer.toString());
      } else if (lookingAt && !matches) {
        if (replacement != null && replacement.length() > 0) {
          StringBuffer result = new StringBuffer();
          matcher.appendReplacement(result, replacement);
          return TestResult.match(matcher.end(), result.toString());
        } else {
          String token = buffer.substring(0, matcher.end());
          return TestResult.match(matcher.end(), token);
        }
      } else {
        return TestResult.fail();
      }
    } else {
      if (matcher.find()) {
        String token = matcher.replaceAll(replacement == null ? "" : replacement);
        return TestResult.match(token.length(), token);
      } else {
        return TestResult.matchContinue(buffer.length(), buffer.toString());
      }
    }
  }

  @Override
  public String toString() {
    return "PatternMatcher["+pattern+"]";
  }
}

