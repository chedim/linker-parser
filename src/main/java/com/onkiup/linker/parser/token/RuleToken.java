package com.onkiup.linker.parser.token;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.annotation.IgnoreCharacters;

public class RuleToken<X extends Rule> implements CompoundToken<X> {
  private X token;
  private Class<X> tokenType;
  private Field[] fields;
  private PartialToken[] values;
  private int nextChild = 0;
  private CompoundToken<?> parent;
  private Field field;
  private String ignoreCharacters = ""; 
  private int nextField;
  private boolean rotated = false;
  private boolean populated = false;
  private boolean failed = false;
  private boolean optional;
  private CharSequence optionalCondition;
  private final ParserLocation location;
  private ParserLocation lastTokenEnd;
  private final Logger logger;

  public RuleToken(CompoundToken<?> parent, Field field, Class<X> type, ParserLocation location) {
    this.tokenType = type;
    this.parent = parent;
    this.field = field;
    this.location = location;
    this.logger = LoggerFactory.getLogger(type);

    try {
      this.token = type.newInstance();
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to instantiate rule token " + type, e);
    }

    fields = Arrays.stream(type.getDeclaredFields())
      .filter(childField -> !Modifier.isTransient(childField.getModifiers()))
      .toArray(Field[]::new);

    values = new PartialToken[fields.length];

    if (parent != null) {
      ignoreCharacters += parent.ignoredCharacters();
    }

    if (type.isAnnotationPresent(IgnoreCharacters.class)) {
      IgnoreCharacters annotation = type.getAnnotation(IgnoreCharacters.class);
      if (!annotation.inherit()) {
        ignoreCharacters = "";
      }
      ignoreCharacters += type.getAnnotation(IgnoreCharacters.class).value();
    }

    optionalCondition = PartialToken.getOptionalCondition(field);
    optional = optionalCondition == null && PartialToken.isOptional(field);
  }

  @Override
  public void sortPriorities() {
    if (rotatable()) {
      if (values[0] instanceof CompoundToken) {
        CompoundToken child = (CompoundToken) values[0];
        if (child.rotatable()) {
          int myPriority = basePriority();
          int childPriority = child.basePriority();
          logger.debug("Verifying priority order for tokens; parent: {} child: {}", myPriority, childPriority);
          if (childPriority < myPriority) {
            logger.debug("Fixing priority order");
            unrotate();
          }
        }
      }
    }
  }

  @Override
  public void markOptional() {
    optional = true;
  }

  @Override
  public boolean isOptional() {
    return optional;
  }

  @Override
  public Optional<Field> targetField() {
    return Optional.ofNullable(field);
  }

  @Override
  public X token() {
    return token;
  }

  @Override 
  public Class<X> tokenType() {
    return tokenType;
  }

  @Override
  public Optional<CompoundToken<?>> parent() {
    return Optional.ofNullable(parent);
  }

  @Override
  public boolean isPopulated() {
    return populated;
  }

  @Override
  public boolean isFailed() {
    return failed;
  }

  @Override
  public String ignoredCharacters() {
    return ignoreCharacters;
  }

  @Override
  public PartialToken<?> nextChild() {
    Field childField = fields[nextChild];
    PartialToken<?> result = PartialToken.forField(this, childField, lastTokenEnd);
    values[nextChild++] = result;
    return result;
  }

  @Override
  public void onChildPopulated() {
    PartialToken<?> child = values[nextChild - 1];
    Field field = fields[nextChild - 1];
    set(field, child.token());
    lastTokenEnd = child.end();
  }

  @Override
  public void onChildFailed() {
    PartialToken<?> child = values[nextChild - 1];
    if (alternativesLeft() == 0 && !child.isOptional()) {
      failed = true;
    }
  }

  @Override
  public void onPopulated(ParserLocation end) {
    this.populated = true;
    lastTokenEnd = end;
  }

  @Override
  public CharSequence traceback() {
    StringBuilder result = new StringBuilder();
    for (int i = nextChild - 1; i > -1; i--) {
      PartialToken<?> child = values[i];
      result.append(child.source());
      if (child.alternativesLeft() > 0) {
        nextChild = i;
        lastTokenEnd = child.location();
        break;
      }
    }
    return result;
  }

  private void set(Field field, Object value) {
    try {
      if (!Modifier.isStatic(field.getModifiers())) {
        logger.debug("Populating field {}", field.getName(), value);
        field.setAccessible(true);
        field.set(token, convert(field.getType(), value));
        try {
          token.reevaluate();
        } catch (Exception e) {
          logger.warn("Failed to reevaluate", e);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to populate field " + field, e);
    }
  }

  protected <T> T convert(Class<T> into, Object what) { 
    if (into.isArray()) {
      Object[] collection = (Object[]) what;
      T[] result = (T[]) Array.newInstance(into.getComponentType(), collection.length);
      int i = 0;
      for (Object item : collection) {
        result[i++] = (T)item;
      }
      return (T) result;
    }


    if (what == null || into.isAssignableFrom(what.getClass())) {
      return (T) what;
    }

    try {
      Constructor<T> constructor = into.getConstructor(String.class);
      constructor.setAccessible(true);
      return constructor.newInstance(what.toString());
    } catch (Exception e) {
      // nothiing to do
    }

    try {
      Method converter = into.getMethod("fromString", String.class);
      converter.setAccessible(true);
      return (T) converter.invoke(null, what.toString());
    } catch (Exception e) {
      // nothiing to do
    }

    try {
      Method converter = into.getMethod("valueOf", String.class);
      converter.setAccessible(true);
      return (T) converter.invoke(null, what.toString());
    } catch (Exception e) {
      throw new RuntimeException("Unable to convert '" + what + "' into " + into, e);
    }
  }

  @Override
  public String toString() {
    return tokenType.getName();
  }

  @Override
  public ParserLocation location() {
    if (values.length > 0) {
      for (int i = 0; i < values.length; i++) {
        if (values[i] != null && values[i].isPopulated()) {
          return values[i].location();
        }
      }
    }
    return location;
  }

  @Override
  public boolean rotatable() {
    if (fields.length < 3) {
      logger.debug("Not rotatable -- not enough fields");
      return false;
    }

    if (!this.isPopulated()) {
      logger.debug("Not rotatable -- not populated");
      return false;
    }

    Field field = fields[0];
    Class fieldType = field.getType();
    if (!fieldType.isAssignableFrom(tokenType)) {
      logger.debug("Not rotatable -- first field is not assignable from token type");
      return false;
    }

    field = fields[fields.length - 1];
    fieldType = field.getType();
    if (!fieldType.isAssignableFrom(tokenType)) {
      logger.debug("Not rotatable -- last field is not assignable from token type");
      return false;
    }

    return true;
  }

  public Field getCurrentField() {
    return fields[nextField > 0 ? nextField - 1 : 0];
  }

  @Override
  public void rotate() {
    logger.info("Rotating");
    token.invalidate();
    RuleToken wrap = new RuleToken(this, fields[0], fields[0].getType(), location);
    wrap.nextChild = nextChild;
    nextChild = 1;
    PartialToken<?>[] wrapValues = wrap.values;
    wrap.values = values;
    values = wrapValues;
    values[0] = wrap;
    X wrapToken = (X) wrap.token();
    wrap.token = token;
    token = wrapToken;
  }

  @Override
  public void unrotate() {
    logger.debug("Un-rotating");
    PartialToken firstToken = values[0];

    CompoundToken<X> kiddo;
    if (firstToken instanceof VariantToken) {
      kiddo = (CompoundToken<X>)((VariantToken<? super X>)kiddo).resolvedAs();
    } else {
      kiddo = (CompoundToken<X>) firstToken;
    }

    Rule childToken = (Rule) kiddo.token();
    Class childTokenType = kiddo.tokenType();

    invalidate();
    kiddo.invalidate();

    PartialToken[] grandChildren = kiddo.children();
    values[0] = grandChildren[grandChildren.length - 1];
    set(fields[0], values[0].token());
    kiddo.token(token);
    kiddo.children(values);

    values = grandChildren;
    values[values.length - 1] = kiddo;
    tokenType = null;
    token = (X) childToken;
    tokenType = (Class<X>) childTokenType;
    children(values);
    set(fields[fields.length - 1], values[values.length - 1].token());
  }

  @Override
  public PartialToken[] children() {
    return values;
  }

  @Override
  public void token(X token) {
    this.token = token;
  }

  @Override
  public void children(PartialToken[] children) {
    this.values = children;
  }

  @Override
  public void invalidate() {
    token.invalidate();
  }

  @Override
  public ParserLocation end() {
    return lastTokenEnd == null ? location : lastTokenEnd;
  }

  @Override
  public StringBuilder source() {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      if (values[i] != null) {
        result.append(values[i].source());
      }
    }
    return result;
  }

}

