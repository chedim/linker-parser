package com.onkiup.linker.parser;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

public final class PartialToken<X> {
  private X token;
  private Set<Field> populatedFields = new HashSet<>();

  protected PartialToken(X token) {
    this.token = token;
  }

  public X getToken() {
    return token;
  }

  public Set<Field> getPopulatedFields() {
    return new HashSet<>(populatedFields);
  }

  public void populateField(Field field, Object value) {
    try {
      if (!Modifier.isFinal(field.getModifiers())) {
        value = (value == null) ? null : convert(field.getType(), value.toString());
        boolean oldAccessible = field.isAccessible();
        field.setAccessible(true);
        field.set(token, value);
        field.setAccessible(oldAccessible);
      }
      populatedFields.add(field);
    } catch (Exception e) {
      throw new RuntimeException("Failed to populate token field '" + field + "' with value '" + value + "'", e);
    }
  }

  public int getPopulatedFieldCount() {
    return populatedFields.size();
  }

  // since: 0.1.1
  protected <T> T convert(Class<T> into, String what) {
    try {
      Constructor<T> constructor = into.getConstructor(String.class);
      constructor.setAccessible(true);
      return constructor.newInstance(what);
    } catch (Exception e) {
      // nothiing to do
    }

    try {
      Method converter = into.getMethod("fromString", String.class);
      converter.setAccessible(true);
      return (T) converter.invoke(null, what);
    } catch (Exception e) {
      // nothiing to do
    }

    try {
      Method converter = into.getMethod("valueOf", String.class);
      converter.setAccessible(true);
      return (T) converter.invoke(null, what);
    } catch (Exception e) {
      throw new RuntimeException("Unable to convert '" + what + "' into " + into);
    }
  }
}

