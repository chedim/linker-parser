package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.SyntaxError;
import com.onkiup.linker.parser.TokenMatcher;
import com.onkiup.linker.parser.TokenTestResult;
import com.onkiup.linker.parser.annotation.OptionalToken;
import com.onkiup.linker.parser.annotation.SkipIfFollowedBy;

public class TerminalToken implements PartialToken<String>, ConsumingToken<String> {


  private Field field;
  private PartialToken<? extends Rule> parent;
  private TokenMatcher matcher;
  private TokenTestResult lastTestResult;
  private CharSequence token;
  private boolean failed = false;
  private boolean isOptional;
  private boolean skipped;
  private CharSequence optionalCondition = "";
  private final ParserLocation start;
  private ParserLocation end;
  private Logger logger;

  public TerminalToken(PartialToken<? extends Rule> parent, Field field, ParserLocation location) {
    this.parent = parent;
    this.field = field;
    this.logger = LoggerFactory.getLogger(field.getDeclaringClass().getName() + "$" + field.getName());
    this.start = this.end = location;
    this.matcher = TokenMatcher.forField(field);
    if (field.isAnnotationPresent(OptionalToken.class)) {
      OptionalToken optional = field.getAnnotation(OptionalToken.class);
      this.isOptional = true;
      this.optionalCondition = optional.whenFollowedBy();
    }

    if (field.isAnnotationPresent(SkipIfFollowedBy.class)) {
      if (isOptional) {
        throw new IllegalStateException("Tokens cannot be both optional and skippable (" + field.getDeclaringClass().getSimpleName() + "." + field.getName() + ")");
      }
      SkipIfFollowedBy condition = field.getAnnotation(SkipIfFollowedBy.class);
      this.isOptional = true;
      this.optionalCondition = condition.value();
    }

    this.setTokenMatcher(matcher);
  }

  public TerminalToken(PartialToken<? extends Rule> parent, TokenMatcher matcher) {
    this.parent = parent;
    this.matcher = matcher;
    this.start = this.end = new ParserLocation("unknown", 0, 0, 0);
  }

  @Override
  public boolean isFailed() {
    return failed;
  }

  @Override
  public Optional<PartialToken> advance(boolean force) throws SyntaxError {
    // returns next token from one of the parents
    return parent.advance(force);
  }

  @Override
  public void onPopulate(CharSequence value) {
    logger.debug("-- MATCHED");
    this.token = value;
    this.end = 
  }

  @Override
  public boolean onFail(CharSequence on) {
    logger.debug("-- FAILED");
    failed = true;
    return !lookahead(on);
  }

  @Override
  public boolean lookahead(CharSequence buffer) {
    if (optionalCondition == null || optionalCondition.length() == 0) {
      logger.debug("the token is unconditionally optional");
      skipped = true;
      return false;
    }
    
    if (optionalCondition.length() > buffer.length()) {
      logger.debug("unable to resolve optionality conditions -- not enough characters -- continuting lookahead");
      return true;
    }

    if (Objects.equals(optionalCondition, buffer.subSequence(0, optionalCondition.length()))) {
      logger.debug("parser input satisfies token optionality condition");
      skipped = true;
    }
    return false;
  }

  private boolean isOptional() {
    return skipped;
  }

  @Override
  public Optional<StringBuilder> pullback() {
    logger.debug("Pullback request received");
    if (isPopulated()) {
      logger.debug("Populated -- Rolling back characters '{}{}'", ignoredCharacters, token);
      StringBuilder result = new StringBuilder()
          .append(ignoredCharacters)
          .append(token);

      token = null;
      return Optional.of(result);
    }
    logger.debug("Not poppulated -- not returning any characters");
    return Optional.empty();
  }

  public boolean skipped() {
    return skipped;
  }

  @Override
  public String getToken() {
    return token;
  }

  @Override
  public Class<String> getTokenType() {
    return String.class;
  }

  @Override
  public boolean isPopulated() {
    return token != null;
  }

  @Override
  public Optional<PartialToken> getParent() {
    return Optional.ofNullable(parent);
  }

  @Override
  public String getIgnoredCharacters() {
    return parent.getIgnoredCharacters();
  }

  private StringBuilder populate(TokenTestResult testResult) {
    token = (String) testResult.getToken();
    int consumed = buffer.length() - cleanBuffer.length() + testResult.getTokenLength();
    buffer.delete(0, consumed);
    return buffer;
  }

  @Override
  public String toString() {
    return field.getDeclaringClass().getName() + "$" + field.getName();
  }

  @Override
  public ParserLocation location() {
    return location;
  }
  
  @Override
  public int consumed() {
    return token != null ? ignoredCharacters.length() + token.length() : buffer.length();
  }

  @Override
  public ParserLocation end() {
    if (token == null) {
      return location.advance(cleanBuffer);
    } else {
      return location.advance(token);
    }
  }

  @Override
  public StringBuilder source() {
    StringBuilder result = new StringBuilder();
    if (token != null && token.length() > 0) {
      result.append(ignoredCharacters.toString()).append(token);
    }
    return result;
  }

  @Override
  public PartialToken expected() {
    return this;
  }

  @Override
  public String tag() {
    return field.getName();
  }
}

