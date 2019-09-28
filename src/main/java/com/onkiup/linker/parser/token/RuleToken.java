package com.onkiup.linker.parser.token;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.annotation.IgnoreCharacters;
import com.onkiup.linker.parser.util.LoggerLayout;

public class RuleToken<X extends Rule> extends AbstractToken<X> implements CompoundToken<X>, Rotatable {
  private X token;
  private Class<X> tokenType;
  private Field[] fields;
  private PartialToken[] values;
  private int nextChild = 0;
  private String ignoreCharacters = ""; 
  private boolean rotated = false;
  private ParserLocation lastTokenEnd;

  public RuleToken(CompoundToken parent, Field field, Class<X> type, ParserLocation location) {
    super(parent, field, location);
    this.tokenType = type;
    this.lastTokenEnd = location;

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
      ignoreCharacters = parent.ignoredCharacters();
    }

    if (type.isAnnotationPresent(IgnoreCharacters.class)) {
      IgnoreCharacters annotation = type.getAnnotation(IgnoreCharacters.class);
      if (!annotation.inherit()) {
        ignoreCharacters = "";
      }
      ignoreCharacters += type.getAnnotation(IgnoreCharacters.class).value();
    }
  }

  @Override
  public void sortPriorities() {
    if (rotatable()) {
      if (values[0] instanceof CompoundToken) {
        CompoundToken child = (CompoundToken) values[0];
        if (child.rotatable()) {
          int myPriority = basePriority();
          int childPriority = child.basePriority();
          log("Verifying priority order for tokens; parent: {} child: {}", myPriority, childPriority);
          if (childPriority < myPriority) {
            log("Fixing priority order");
            rotateBack();
          }
        }
      }
    }
  }

  @Override
  public Optional<X> token() {
    return Optional.ofNullable(token).map(token -> {
      Rule.Metadata.metadata(token, this);
      return token;
    });
  }

  @Override 
  public Class<X> tokenType() {
    return tokenType;
  }

  @Override
  public String ignoredCharacters() {
    return ignoreCharacters;
  }

  @Override
  public Optional<PartialToken<?>> nextChild() {
    if (nextChild >= fields.length) {
      return Optional.empty();
    }
    if (values[nextChild] == null || values[nextChild].isFailed() || values[nextChild].isPopulated()) {
      Field childField = fields[nextChild];
      PartialToken<?> result = PartialToken.forField(this, childField, lastTokenEnd);
      return Optional.of(values[nextChild++] = result);
    } else {
      return Optional.of(values[nextChild++]);
    }
  }

  @Override
  public void onChildPopulated() {
    PartialToken<?> child = values[nextChild - 1];
    Field field = fields[nextChild - 1];
    set(field, child.token().orElse(null));
    lastTokenEnd = child.end();
    if (nextChild >= fields.length) {
      onPopulated(lastTokenEnd);
    }
  }

  public Field[] fields() {
    return fields;
  }

  @Override
  public void onChildFailed() {
    PartialToken<?> child = values[nextChild - 1];
    if (child.isOptional()) {
      if (nextChild >= fields.length) {
        onPopulated(lastTokenEnd);
      }
    } else if (alternativesLeft() == 0) {
      onFail();
    }
  }

  private void set(Field field, Object value) {
    log("Trying to set field ${} to '{}'", field.getName(), LoggerLayout.sanitize(value));
    try {
      if (!Modifier.isStatic(field.getModifiers())) {
        log("Setting field ${} to '{}'", field.getName(), LoggerLayout.sanitize(value));
        field.setAccessible(true);
        field.set(token, convert(field.getType(), value));
        try {
          token.reevaluate();
        } catch (Exception e) {
          error("Failed to reevaluate", e);
        }
      } else {
        log("NOT Setting field {} to '{}' -- the field is static", field.getName(), LoggerLayout.sanitize(value));
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
  public String tag() {
    return tokenType.getName();
  }

  @Override
  public String toString() {
    return String.format("%s || %s (positoin: %d)", tail(50), tokenType.getName() + (nextChild > - 1 ? "$" + (fields[nextChild - 1].getName()) : ""), position());
  }

  @Override
  public boolean rotatable() {
    if (fields.length < 3) {
      log("Not rotatable -- not enough fields");
      return false;
    }

    if (!this.isPopulated()) {
      log("Not rotatable -- not populated");
      return false;
    }

    Field field = fields[0];
    Class fieldType = field.getType();
    if (!fieldType.isAssignableFrom(tokenType)) {
      log("Not rotatable -- first field is not assignable from token type");
      return false;
    }

    field = fields[fields.length - 1];
    fieldType = field.getType();
    if (!fieldType.isAssignableFrom(tokenType)) {
      log("Not rotatable -- last field is not assignable from token type");
      return false;
    }

    return true;
  }

  @Override
  public void rotateForth() {
    log("Rotating");
    token.invalidate();
    RuleToken wrap = new RuleToken(this, fields[0], fields[0].getType(), location());
    wrap.nextChild = nextChild;
    nextChild = 1;
    PartialToken<?>[] wrapValues = wrap.values;
    wrap.values = values;
    values = wrapValues;
    values[0] = wrap;
    X wrapToken = (X) wrap.token().orElse(null);
    wrap.token = token;
    token = wrapToken;
  }

  @Override
  public void rotateBack() {
    log("Un-rotating");
    PartialToken firstToken = values[0];

    CompoundToken<X> kiddo;
    if (firstToken instanceof VariantToken) {
      kiddo = (CompoundToken<X>)((VariantToken<? super X>)firstToken).resolvedAs().orElse(null);
    } else {
      kiddo = (CompoundToken<X>) firstToken;
    }

    Rule childToken = (Rule) kiddo.token().orElse(null);
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
    set(fields[fields.length - 1], values[values.length - 1].token().orElse(null));
  }

  @Override
  public PartialToken[] children() {
    return values;
  }

  @Override
  public int unfilledChildren() {
    return fields.length - nextChild;
  }

  @Override
  public int currentChild() {
    return nextChild - 1;
  }

  @Override
  public void nextChild(int newIndex) {
    nextChild = newIndex;
  }

  @Override
  public void token(X token) {
    this.token = token;
  }

  @Override
  public void children(PartialToken[] children) {
    lastTokenEnd = location();
    for (int i = 0; i < values.length; i++) {
      if (i < children.length) {
        values[i] = children[i];
      } else {
        values[i] = null;
      }
      if (values[i] != null && values[i].isPopulated()) {
        lastTokenEnd = values[i].end();
      }
    }
  }

  @Override
  public void invalidate() {
    token.invalidate();
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

