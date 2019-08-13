package com.onkiup.linker.parser;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;


// in 0.2.2:
// - bound X to Rule
// - added C type parameter
// - added evauation logic
public final class PartialToken<C, X> {
  private static final Reflections reflections  = new Reflections(new ConfigurationBuilder()
        .setUrls(ClasspathHelper.forClassLoader(TokenGrammar.class.getClassLoader()))
        .setScanners(new SubTypesScanner(true))
    );

  private Class<X> tokenType;
  private X token;
  private Field[] fields;
  private Object[] values;
  private int populatedFields = 0;
  private int currentAlternative = 0;
  private List<Class<? extends X>> variants;
  private List<String> variantFails;
  private LinkedList<Object> collection = new LinkedList<>();
  private boolean populated = false;
  private TokenMatcher matcher;
  private ParserLocation location;

  protected PartialToken(Class<X> tokenType, ParserLocation location) { 
    this.tokenType = tokenType;
    this.location = location;
    if (!TokenGrammar.isConcrete(tokenType)) {
      variants = reflections.getSubTypesOf(tokenType).stream()
        .filter(TokenGrammar::isConcrete)
        .collect(Collectors.toList());
      variantFails = new LinkedList<>();
    } else if (Rule.class.isAssignableFrom(tokenType)) {
      this.fields = tokenType.getDeclaredFields();
      this.values = new Object[this.fields.length];
    }
  }

  public void finalize(String value) {
    if (token != null) {
      throw new IllegalStateException("Already finalized");
    }

    if (fields != null) {
      throw new IllegalStateException("Cannot finalize compound token with terminal value");
    }

    this.token = convert(tokenType, value);
    populated = true;
  }

  public void resolve(Object value) {
    if (token != null) {
      throw new IllegalStateException("Already finalized");
    }

    if (variants == null || variants.size() == 0) {
      throw new RuntimeException("Unable to finalize concrete token as junction");
    }

    token = (X) value;
    populated = true;
  }

  public X finalize(C context) {
    if (token != null) {
      throw new IllegalStateException("Already finalized");
    }

    if (fields == null && !tokenType.isArray()) { 
      throw new IllegalStateException("Cannot finalize terminal token with compound values");
    }

    try {
      if (tokenType.isArray()) {
        token = (X) collection.toArray();
        return token;
      } else {
        token = tokenType.newInstance();
        for (int i = 0; i < fields.length; i++) {
          Field field = fields[i];
          Object value = values[i];
          try {
            if (!Modifier.isFinal(field.getModifiers())) {
              Class fieldType = field.getType();
              value = (value == null) ? null : convert(fieldType, value);
              boolean oldAccessible = field.isAccessible();
              field.setAccessible(true);
              field.set(token, value);
              field.setAccessible(oldAccessible);
            }
          } catch (Exception e) {
            throw new RuntimeException("Failed to populate token field '" + field + "' with value '" + value + "'", e);
          }
        }
      }

      if (context != null) {
        ((Rule<C>) token).accept(context);
      }
      return token;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public X getToken() {
    return token;
  } 
  public Class<X> getTokenType() {
    return tokenType;
  }

  public Field[] getFields() {
    return fields;
  }

  public Field getCurrentField() {
    if (fields == null) {
      return null;
    }
    return fields[populatedFields];
  }

  public void populateField(Object value) {
    values[populatedFields++] = value;
    populated = fields.length <= populatedFields;
  }

  public int getPopulatedFieldCount() {
    return populatedFields;
  }

  public int getFieldCount() {
    return fields.length;
  }

  public boolean isPopulated() {
    return populated;
  }

  public boolean isFieldOptional() {
    Field field = getCurrentField();
    return field != null && field.isAnnotationPresent(Optional.class);
  }

  public boolean hasRequiredFields() {
    if (fields != null) {
      for(int i = populatedFields; i < fields.length; i++) {
        Field field = fields[i];
        if (!field.isAnnotationPresent(Optional.class)) {
          return true;
        }
      }
    }

    return false;
  }

  public void setPopulated() {
    this.populated = true;
  }

  public boolean hasAlternativesLeft() {
    return variants != null && currentAlternative < variants.size() - 1;
  }

  public Class<? extends X> getCurrentAlternative() {
    return variants.get(currentAlternative);
  }

  public Class<? extends X> advanceAlternative(String where) {
    variantFails.add(where);
    currentAlternative++;
    return variants.get(currentAlternative); 
  }

  // since: 0.1.1
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

  public void add(Object x) {
    collection.add(x);
  }

  public int getCollectionSize() {
    return collection.size();
  }

  public List<Object> getCollection() {
    return collection;
  }

  public void setMatcher(TokenMatcher matcher) {
    this.matcher = matcher;
  }

  public TokenMatcher getMatcher() {
    return this.matcher;
  }

  @Override
  public String toString() {
    String memberId = collection.size() > 0 ? "#" + collection.size() : 
        variants != null && variants.size() > 0 ? "?" + currentAlternative : "";
    String members = collection.size() > 0 ? '\n' + collection.stream().map(Object::toString).collect(Collectors.joining(", ")) : "";
    return new StringBuilder()
      .append(String.format("%-32s", tokenType.getSimpleName() + memberId))
      .append(" @ ")
      .append(location.toString())
      .append(members)
      .toString();
  }
}

