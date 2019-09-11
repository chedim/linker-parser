package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
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
  private String token;
  private StringBuilder buffer = new StringBuilder();
  private StringBuilder cleanBuffer = new StringBuilder();
  private StringBuilder ignoredCharacters = new StringBuilder();
  private boolean failed = false;
  private boolean isOptional;
  private boolean isSkippable;
  private boolean skipped;
  private String optionalCondition = "";
  private ParserLocation location;
  private Logger logger;

  public TerminalToken(PartialToken<? extends Rule> parent, Field field, ParserLocation location) {
    this.parent = parent;
    this.field = field;
    this.logger = LoggerFactory.getLogger(field.getDeclaringClass().getName() + "$" + field.getName());
    this.location = location;
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
      this.isSkippable = true;
      this.optionalCondition = condition.value();
    }
  }

  public TerminalToken(PartialToken<? extends Rule> parent, TokenMatcher matcher) {
    this.parent = parent;
    this.matcher = matcher;
    this.location = new ParserLocation("unknown", 0, 0, 0);
  }

  @Override
  public Optional<PartialToken> advance(boolean force) throws SyntaxError {
    // returns next token from one of the parents
    return parent.advance(force);
  }

  @Override
  public Optional<StringBuilder> consume(char character, boolean last) {

    if (token != null) {
      return Optional.of(new StringBuilder().append(character));
    }

    String ignoreCharacters = parent == null ? null : parent.getIgnoredCharacters();

    buffer.append(character);

    if (ignoreCharacters != null) {
      if (cleanBuffer.length() == 0) {
        if (ignoreCharacters.indexOf(character) == -1) {
          logger.debug("Did not find character {} in ignored character list", (int) character);
          cleanBuffer.append(character);
        } else if (last) {
          cleanBuffer.append(character);
          StringBuilder ret = cleanBuffer;
          cleanBuffer = new StringBuilder();
          return Optional.of(ret);
        } else {
          logger.debug("Ignoring character with code " + (int) character);
          ignoredCharacters.append(character);
          location = location.advance("" + character);
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
      StringBuilder returnBuffer = new StringBuilder();

      boolean callParent = !(isOptional || isSkippable);
      if (optionalCondition.length() > 0) {
        logger.debug("Testing optional condition '{}'", optionalCondition);
        String followed = cleanBuffer.toString();
        if (!optionalCondition.equals(followed)) {
          if (optionalCondition.startsWith(followed)) {
            logger.debug("Need to consume more characters to decide if token is optional");
            return Optional.empty();
          }
          logger.debug("Token is not optional as it is followed by '{}' and not '{}'", cleanBuffer, optionalCondition);
          callParent = true;
        }
      }

      if (callParent) {
        logger.debug("Token is not optional; pushing back to parent");
        getParent()
          .flatMap(p -> p.pushback(true))
          .ifPresent(b -> {
            logger.debug("Received characters from pushed back parent: '{}'", b);
            returnBuffer.insert(0, b);
          });
      } else {
        logger.debug("Token is optional; returning consumed characters without notifying parent");
        skipped = true;
      }

      returnBuffer.append(buffer);
      buffer = new StringBuilder();
      logger.debug("Returning back to parser buffer: '{}'", returnBuffer); 
      return Optional.of(returnBuffer);
    } else if (lastTestResult.isMatch() || (lastTestResult.isMatchContinue() && last)) {
      logger.debug("Test suceeded (forced: {}) on buffer '{}' using matcher {}", last, cleanBuffer, matcher);

      if (isSkippable && !last) {
        String after = cleanBuffer.substring(lastTestResult.getTokenLength());
        if (after.length() < optionalCondition.length()) {
          logger.debug("Token is skippable -- need more input to detect if it should be skipped");
          return Optional.empty();
        } else if (after.startsWith(optionalCondition)) {
          logger.debug("Skipping this token as it is followed by '{}'", optionalCondition);
          StringBuilder returnBuffer = buffer;
          buffer = new StringBuilder();
          return Optional.of(returnBuffer);
        }
      }

      StringBuilder result = new StringBuilder(populate(lastTestResult).toString());
      buffer = new StringBuilder();
      return Optional.of(result);
    }
    return Optional.empty();
  }

  private boolean isOptional() {
    return field.isAnnotationPresent(OptionalToken.class);
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

