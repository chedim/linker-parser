package com.onkiup.linker.parser;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

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
  private Collection<Class<? extends X>> variants;
  private LinkedList<Object> collection = new LinkedList<>();
  private boolean populated = false;
  private Matcher<X> matcher;

  protected PartialToken(Class<X> tokenType) {
    this.tokenType = tokenType;
    if (!TokenGrammar.isConcrete(tokenType)) {
      variants = reflections.getSubtypesOf(type).stream()
        .filter(TokenGrammar::isConcrete)
        .collect(Collectors.toList());
    } else if (Rule.class.isAssignableFrom(tokenType) {
      this.fields = tokenType.getDeclaredFields();
      this.values[] = new Object[this.fields.length];
    } else {
      this.fields = new Field[1];
      this.values = new Object[1];
    }
  }

  public X finalize(C context) {
    if (token != null) {
      throw new InvalidStateException("Already finalized");
    }

    try {
      if (Rule.class.isAssignableFrom(rokenType)) {
        token = tokenType.newInstance();
        for (int i = 0; i < fields.length()) {
          Field field = fields[i];
          try {
            if (!Modifier.isFinal(field.getModifiers())) {
              Object value = values[i];
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
        } else {
          token = convert(tokenType, values[0]);
        }
      }

      if (context != null && token instanceof Rule) {
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

  public Field getNextField() {
    return fields[populatedFields];
  }

  public void populateField(Object value) {
    values[populatedFields++] = value;
    populated = fields.length >= populatedFields;
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

  public void setPopulated() {
    this.populated = true;
  }

  public boolean hasAlternativesLeft() {
    return currentAlternative < alternatives.size() - 1;
  }

  public Class<? extends X> getCurrentAlternative() {
    return alternatives.get(currentAlternative);
  }

  public Class<? extends X> advanceAlternative() {
    currentAlternative++;
    return alternatives.get(currentAlternative); 
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

  public void add(Object x) {
    collection.add(x);
  }

  public int getCollectionSize() {
    return collection.size();
  }

  public List<Object> getCollection() {
    return collection;
  }

  public void setMatcher(Matcher<X> matcher) {
    this.matcher = matcher;
  }

  public Matcher<X> getMatcher() {
    return this.matcher;
  }
}

