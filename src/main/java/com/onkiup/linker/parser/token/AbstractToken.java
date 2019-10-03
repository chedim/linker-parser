package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onkiup.linker.parser.ParserLocation;

public abstract class AbstractToken<X> implements PartialToken<X> {

  private CompoundToken<?> parent;
  private Field field;
  private ParserLocation location, end;
  private boolean optional, populated, failed;
  private CharSequence optionalCondition;
  private Logger logger;
  private LinkedList metatokens = new LinkedList();

  public AbstractToken(CompoundToken<?> parent, Field targetField, ParserLocation location) {
    this.parent = parent;
    this.field = targetField;
    this.location = location;

    readFlags(field);
  }

  @Override
  public void markOptional() {
    log("marked optional");
    this.optional = true;
  }

  @Override
  public boolean isOptional() {
    return optional;
  }

  @Override
  public boolean isPopulated() {
    return populated;
  }

  @Override
  public void dropPopulated() {
    populated = false;
    log("Dropped population flag");
  }

  @Override
  public boolean isFailed() {
    return failed;
  }

  @Override
  public ParserLocation location() {
    return location;
  }

  protected void location(ParserLocation location) {
    this.location = location;
  }

  @Override
  public ParserLocation end() {
    return this.end == null ? this.location : this.end;
  }

  @Override
  public Optional<CompoundToken<?>> parent() {
    return Optional.ofNullable(parent);
  }

  @Override
  public Optional<Field> targetField() {
    return Optional.ofNullable(field);
  }

  @Override
  public void onPopulated(ParserLocation end) {
    log("populated up to {}", end.position());
    populated = true;
    failed = false;
    this.end = end;
  }

  @Override
  public Logger logger() {
    if (logger == null) {
      logger = LoggerFactory.getLogger(tag());
    }
    return logger;
  }

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

  protected void readFlags(Field field) {
    optionalCondition = PartialToken.getOptionalCondition(field).orElse(null);
    optional = optionalCondition == null && PartialToken.hasOptionalAnnotation(field);
  }

  @Override
  public void onFail() {
    failed = true;
    populated = false;
    end = location;
    PartialToken.super.onFail();
  }

  public Optional<CharSequence> optionalCondition() {
    return Optional.ofNullable(optionalCondition);
  }

  @Override
  public void addMetaToken(Object metatoken) {
    metatokens.add(metatoken);
  }

  @Override
  public LinkedList<?> metaTokens() {
    return metatokens;
  }
}

