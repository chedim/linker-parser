package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.SyntaxError;
import com.onkiup.linker.parser.TokenMatcher;
import com.onkiup.linker.parser.TokenTestResult;

public class TerminalToken implements PartialToken<String>, ConsumingToken<String> {

  private static Logger logger = LoggerFactory.getLogger(TerminalToken.class);

  private Field field;
  private PartialToken<? extends Rule> parent;
  private TokenMatcher matcher;
  private TokenTestResult lastTestResult;
  private String token;
  private StringBuilder buffer = new StringBuilder();
  private StringBuilder cleanBuffer = new StringBuilder();
  private StringBuilder ignoredCharacters = new StringBuilder();
  private final int position;
  private boolean failed = false;
  private boolean isOptional;
  private String optionalCondition = "";

  public TerminalToken(PartialToken<? extends Rule> parent, Field field, int position) {
    this.parent = parent;
    this.field = field;
    this.position = position;
    this.matcher = TokenMatcher.forField(field);
    if (field.isAnnotationPresent(com.onkiup.linker.parser.annotation.Optional.class)) {
      com.onkiup.linker.parser.annotation.Optional optional = field.getAnnotation(com.onkiup.linker.parser.annotation.Optional.class);
      this.isOptional = true;
      this.optionalCondition = optional.whenFollowedBy();
    }
  }

  public TerminalToken(PartialToken<? extends Rule> parent, TokenMatcher matcher) {
    this.parent = parent;
    this.matcher = matcher;
    this.position = 0;
  }

  @Override
  public Optional<PartialToken> advance(boolean force) throws SyntaxError {
    // returns next token from one of the parents
    return parent.advance(force);
  }

  @Override
  public Optional<StringBuilder> consume(char character, boolean last) {
    String ignoreCharacters = parent == null ? null : parent.getIgnoredCharacters();

    buffer.append(character);

    if (ignoreCharacters != null) {
      if (cleanBuffer.length() == 0) {
        if (ignoreCharacters.indexOf(character) == -1) {
          logger.debug("Did not find character {} in ignored character list", (int) character);
          cleanBuffer.append(character);
        } else {
          logger.debug("Ignoring character with code " + (int) character);
          ignoredCharacters.append(character);
        }
      } else {
        logger.debug("Clean buffer is not empty, not testing if the character should be ignored");
        cleanBuffer.append(character);
      }
    } else {
      logger.info("Accepting all characters as ignored characters list was empty");
      cleanBuffer.append(character);
    }

    if (cleanBuffer.length() == 0) {
      return Optional.empty();
    }

    if (!failed) {
      lastTestResult = matcher.apply(cleanBuffer);
    }

    if (failed || lastTestResult.isFailed()) {
      failed = true;
      logger.debug("Test failed on buffer '{}' using matcher {}", cleanBuffer, matcher);
      StringBuilder returnBuffer = new StringBuilder().append(buffer.toString());

      boolean callParent = !isOptional;
      if (optionalCondition.length() > 0) {
        String followed = cleanBuffer.toString();
        if (!optionalCondition.equals(followed)) {
          if (optionalCondition.startsWith(followed)) {
            logger.debug("Need to consume more characters to decide if token {} is optional", this);
            return Optional.empty();
          }
          logger.debug("Token {} is not optional as it is followed by '{}' and not '{}'", this, cleanBuffer, optionalCondition);
          callParent = true;
        }
      }

      if (callParent) {
        logger.debug("Token {} is not optional; pushing back to parent", this);
        getParent()
          .flatMap(p -> p.pushback(true))
          .ifPresent(b -> returnBuffer.insert(0, b));
      } else {
        logger.debug("Token {} is optional; returning consumed characters without notifying parent", this);
      }

      logger.debug("Returning back to parser buffer: '{}'", returnBuffer); 
      return Optional.of(returnBuffer);
    } else if (lastTestResult.isMatch() || (lastTestResult.isMatchContinue() && last)) {
      logger.debug("Test suceeded (forced: {}) on buffer '{}' using matcher {}", last, cleanBuffer, matcher);
      return Optional.of(populate(lastTestResult));
    }
    return Optional.empty();
  }

  private boolean isOptional() {
    return field.isAnnotationPresent(com.onkiup.linker.parser.annotation.Optional.class);
  }

  @Override
  public Optional<StringBuilder> pullback() {
    if (isPopulated()) {
      logger.debug("{}: Rolling back characters '{}{}'", this, ignoredCharacters, buffer);
      return Optional.of(new StringBuilder()
          .append(ignoredCharacters)
          .append(token)
      );
    }
    return Optional.empty();
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
    return "TerminalToken[" + matcher + "]@[" + position + " - " + (position + consumed()) + "]";
  }

  @Override
  public int position() {
    return position;
  }
  
  @Override
  public int consumed() {
    return token != null ? ignoredCharacters.length() + token.length() : buffer.length();
  }
}

