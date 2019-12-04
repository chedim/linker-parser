package com.onkiup.linker.parser.token;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onkiup.linker.parser.ParserLocation;

/**
 * Common implementation for PartialTokens
 * @param <X> type of resulting token
 */
public abstract class AbstractToken<X> implements PartialToken<X>, Serializable {

  private CompoundToken<?> parent;
  private PartialToken<?> previousToken, nextToken;
  /**
   * The field for which this token was created
   */
  private Field field;
  /**
   * location of the first character matched with the token and the next character after the last character matched with the token
   */
  private ParserLocation location, end;
  /**
   * Token status flags
   */
  private boolean optional, populated, failed;
  /**
   * Token optionality condition
   */
  private CharSequence optionalCondition;
  private transient Logger logger;
  private LinkedList metatokens = new LinkedList();
  private final int childNumber;

  /**
   * Main constructor
   * @param parent parent token
   * @param targetField field for which this token is being constructed
   * @param location token's location in parser's buffer
   */
  public AbstractToken(CompoundToken<?> parent, int childNumber, Field targetField, ParserLocation location) {
    this.parent = parent;
    this.field = targetField;
    this.location = location;
    this.childNumber = childNumber;

    readFlags(field);
  }

  public void previousToken(PartialToken<?> previousToken) {
    this.previousToken = previousToken;
  }

  public void nextToken(PartialToken<?> nextToken) {
    this.nextToken = nextToken;
  }

  /**
   * Sets optionality flag on this token: optional tokens don't propagate matching failures to their parents
   */
  @Override
  public void markOptional() {
    log("marked optional");
    this.optional = true;
  }

  /**
   * @return true if this token is optional
   */
  @Override
  public boolean isOptional() {
    return optional;
  }

  /**
   * @return true if this token was successfully populated
   */
  @Override
  public boolean isPopulated() {
    return populated;
  }

  /**
   * resets token population flag
   */
  @Override
  public void dropPopulated() {
    populated = false;
    log("Dropped population flag");
  }

  /**
   * @return true if this token did not match the source
   */
  @Override
  public boolean isFailed() {
    return failed;
  }

  /**
   * @return location of this token in parser's input
   */
  @Override
  public ParserLocation location() {
    return location;
  }

  /**
   * Sets location of this token in parser's input
   * @param location new token location
   */
  protected void location(ParserLocation location) {
    this.location = location;
  }

  /**
   * @return location that immediately follows the last character matched with this token
   */
  @Override
  public ParserLocation end() {
    return this.end == null ? this.location : this.end;
  }

  /**
   * @return parent token
   */
  @Override
  public Optional<CompoundToken<?>> parent() {
    return Optional.ofNullable(parent);
  }

  /**
   * @return the field for which this token was created
   */
  @Override
  public Optional<Field> targetField() {
    return Optional.ofNullable(field);
  }

  /**
   * Handler for token population event
   * @param end location after the last character matched with this token
   */
  @Override
  public void onPopulated(ParserLocation end) {
    log("populated up to {}", end.position());
    populated = true;
    failed = false;
    this.end = end;
  }

  /**
   * @return logger configured with information about matching token
   */
  @Override
  public Logger logger() {
    if (logger == null) {
      logger = LoggerFactory.getLogger(tag());
    }
    return logger;
  }

  /**
   * @return token identifier to be used in logs
   */
  @Override
  public String tag() {
    return targetField()
        .map(field -> field.getDeclaringClass().getName() + "$" + field.getName() + "(" + position() + ")")
        .orElseGet(super::toString);
  }

  @Override
  public String toString() {
    ParserLocation location = location();
    return targetField()
        .map(field -> String.format(
            "%50.50s || %s (%d:%d -- %d - %d)",
            head(50),
            field.getDeclaringClass().getName() + "$" + field.getName(),
            location.line(),
            location.column(),
            location.position(),
            end().position()
        ))
        .orElseGet(super::toString);
  }

  /**
   * reads optionality configuration for the field
   * @param field field to read the configuration from
   */
  protected void readFlags(Field field) {
    optionalCondition = PartialToken.getOptionalCondition(field).orElse(null);
    optional = optionalCondition == null && PartialToken.hasOptionalAnnotation(field);
  }

  /**
   * Handler that will be invoked upon token matching failure
   */
  @Override
  public void onFail() {
    failed = true;
    populated = false;
    end = location;
    PartialToken.super.onFail();
  }

  /**
   * @return characters that must appear in place of the token in order for the token to be considered optional
   */
  public Optional<CharSequence> optionalCondition() {
    return Optional.ofNullable(optionalCondition);
  }

  /**
   * Stores a metatoken under this token
   * @param metatoken object to store as metatoken
   */
  @Override
  public void addMetaToken(Object metatoken) {
    metatokens.add(metatoken);
  }

  /**
   * @return all metatokens for this token
   */
  @Override
  public LinkedList<?> metaTokens() {
    return metatokens;
  }

  @Override
  public int position() {
    return childNumber;
  }

  @Override
  public Optional<PartialToken<?>> nextToken() {
    return Optional.empty();
  }

  @Override
  public Optional<PartialToken<?>> previousToken() {
    return Optional.empty();
  }
}

