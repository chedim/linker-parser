package com.onkiup.linker.parser;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// in 0.4:
// - removed evaluation callbacks
// - removed C type parameter
// - Added parent
// - Fields are now populated in populateField and not finalize(C)
// in 0.2.2:
// - bound X to Rule
// - added C type parameter
// - added evauation logic
public final class PartialToken<X> {
  private static final Logger logger = LoggerFactory.getLogger(PartialToken.class);
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
  private boolean finalized = false;
  private TokenMatcher matcher;
  private ParserLocation location;
  private StringBuilder taken = new StringBuilder();
  private String ignoreCharacters;
  private PartialToken<?> parent;

  protected PartialToken(PartialToken<?> parent, Class<X> tokenType, ParserState state) { 
    this.parent = parent;
    this.tokenType = tokenType;
    if (state != null) {
      this.location = state.location();
    }
    if (!TokenGrammar.isConcrete(tokenType)) {
      if (tokenType.isAnnotationPresent(Alternatives.class)) {
        variants = Arrays.asList(tokenType.getAnnotation(Alternatives.class).value());
      } else {
        final ConcurrentHashMap<Class, Integer> typePriorities = new ConcurrentHashMap<>();
        variants = reflections.getSubTypesOf(tokenType).stream()
          .filter(type -> {
            Class superClass = type.getSuperclass();
            if (superClass != tokenType) {
              Class[] interfaces = type.getInterfaces();
              for (Class iface : interfaces) {
                if (iface == tokenType) {
                  return true;
                }
              }
              logger.info("Ignoring " + type + " (extends: " + superClass + ") ");
              return false;
            }
            return true;
          })
          .sorted((sub1, sub2) -> {
            if (!typePriorities.containsKey(sub1)) {
              typePriorities.put(sub1, calculatePriority(sub1, state));
            }
            if (!typePriorities.containsKey(sub2)) {
              typePriorities.put(sub2, calculatePriority(sub2, state));
            }

            return Integer.compare(typePriorities.get(sub1), typePriorities.get(sub2));
          })
          .collect(Collectors.toList());
      }
      variantFails = new LinkedList<>();
    } else if (Rule.class.isAssignableFrom(tokenType)) {
      this.fields = tokenType.getDeclaredFields();
      this.values = new Object[this.fields.length];
      try {
        token = tokenType.newInstance();
        Rule.Metadata.metadata((Rule)token, this);
      } catch (Exception e) {
        throw new RuntimeException("Failed to instantiate " + tokenType, e);
      }
    }

    if (tokenType.isAnnotationPresent(IgnoreCharacters.class)) {
      ignoreCharacters = tokenType.getAnnotation(IgnoreCharacters.class).value();
    }
  }

  private static int calculatePriority(Class<?> type, ParserState state) {
    int result = 0;
    if (!TokenGrammar.isConcrete(type)) {
      result += 1000;
    }
    if (state != null && state.isInTrace(type)) {
      result += 1000;
    }

    if (type.isAnnotationPresent(AdjustPriority.class)) {
      AdjustPriority adjust = type.getAnnotation(AdjustPriority.class);
      result += adjust.value();
      logger.info("Adjusted priority by " + adjust.value()); 
    }

    logger.info(type.getSimpleName() + " priority " + result);

    return result;
  }

  public String getIgnoreCharacters() {
    return ignoreCharacters;
  }

  public void appendIgnoreCharacters(String characters) {
    if (characters == null) {
      return;
    }
    if (ignoreCharacters == null) {
      ignoreCharacters = characters;
    } else {
      ignoreCharacters += characters;
    }
  }

  public void finalize(String value) {
    if (finalized) {
      throw new IllegalStateException("Already finalized");
    }

    if (fields != null) {
      throw new IllegalStateException("Cannot finalize compound token with terminal value");
    }

    this.token = convert(tokenType, value);
    populated = true;
    finalized = true;
  }

  public void resolve(Object value) {
    if (finalized) {
      throw new IllegalStateException("Already finalized");
    }

    if (variants == null || variants.size() == 0) {
      throw new RuntimeException("Unable to finalize concrete token as junction");
    }

    token = (X) value;
    populated = true;
    finalized = true;
    logger.info("Resolved '" + tokenType + "' to " + token);
  }

  public X finalizeToken() {
    if (finalized) {
      throw new IllegalStateException("Already finalized");
    }

    if (fields == null && !tokenType.isArray()) { 
      throw new IllegalStateException("Cannot finalize terminal token with compound values");
    }

    logger.info("Finalizing " + this);

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

      finalized = true;
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
    int populatedFields = getPopulatedFieldCount();
    if (fields == null || populated) {
      return null;
    }

    return fields[populatedFields];
  }

  public void populateField(Object value) {
    int populatedFields = getPopulatedFieldCount();
    Field field = fields[populatedFields];
    Class fieldType = field.getType();
    logger.info("Populating field " + field.getName() + " with value '" + value + "' ");
    if (fieldType.isArray() && field.isAnnotationPresent(CaptureLimit.class)) {
      CaptureLimit limit = field.getAnnotation(CaptureLimit.class);
      Object[] values = (Object[]) value;
      if (values.length < limit.min()) {
        throw new IllegalStateException("Expected at least " + limit.min() + " entries of '" + field.getType().getComponentType() + "' but got " + values.length);
      }
      if (values.length > limit.max()) {
        throw new IllegalStateException("Expected at most " + limit.max() + " entries of '" + field.getType().getComponentType() + "' but got " + values.length);
      }
    }

    value = value == null ? null : convert(fieldType, value);

    // do we have a populator?
    try {
      Method populator = tokenType.getMethod(field.getName(), fieldType);
      // sure we do!
      populator.invoke(token, value);
    } catch (NoSuchMethodException nsme) {
      try {
        // nope, we don't
        if (!Modifier.isStatic(field.getModifiers())) {
          field.setAccessible(true);
          field.set(token, value);
        }
      } catch (Throwable t) {
        throw new RuntimeException("Failded to populate field " + field, t);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to populate field " + field, e);
    }

    ((Rule)token).reevaluate();

    values[populatedFields] = value;
    this.populatedFields = populatedFields + 1;
  }
  
  public void appendTaken(String characters) {
    taken.append(characters);
    logger.info("Taken: '" + taken + "'");
  }

  public StringBuilder getTaken() {
    return taken;
  }

  public int getPopulatedFieldCount() {
    if (fields != null && !populated) {
      while (fields != null && populatedFields < fields.length && isTransient(fields[populatedFields])) {
        populatedFields++;
      }
      populated = populatedFields >= fields.length;
    }
    return populatedFields;
  }

  public int getFieldCount() {
    return fields.length;
  }

  public boolean isPopulated() {
    return populated || (fields != null && fields.length <= getPopulatedFieldCount());
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

  public boolean hasAlternatives() {
    return variants != null && variants.size() > 0;
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

  public TokenTestResult test(StringBuilder buffer) {
    StringBuilder cleaned; 
    int ignoredCharacters = 0;
    if (ignoreCharacters != null) {
      cleaned = new StringBuilder();
      for (ignoredCharacters = 0; ignoredCharacters < buffer.length(); ignoredCharacters++) {
        if (ignoreCharacters.indexOf(buffer.charAt(ignoredCharacters)) < 0) {
          cleaned.append(buffer.substring(ignoredCharacters));
          break;
        }
      }
    } else {
      cleaned = buffer;
    }

    logger.info("Ignored " + ignoredCharacters + " characters: '" + buffer + "' -> '"+ cleaned + "'");
    if (cleaned.length() == 0) {
      return TestResult.matchContinue(buffer.length(), buffer);
    }
    TokenTestResult result = matcher.apply(cleaned);
    result.setTokenLength(result.getTokenLength() + ignoredCharacters);
    return result;
  }

  @Override
  public String toString() {
    String memberId = collection.size() > 0 ? "#" + collection.size() : 
        variants != null && variants.size() > 0 ? "?" + (currentAlternative + 1) + "/" + variants.size() : "";
    String members = collection.size() > 0 ? "\n\t" + collection.stream().map(Object::toString).collect(Collectors.joining("\n\t")) : "";
    StringBuilder fieldsDump = new StringBuilder();
    if (fields != null) {
      for (int i = populatedFields; i > -1; i--) {
        fieldsDump.append("\t");
        if (i == populatedFields) {
          fieldsDump.append("-->");
        } else {
          fieldsDump.append("   ");
        }
        if (i < fields.length) {
          Class fieldType = fields[i].getType(); 
          fieldsDump.append(" " + fieldType.getSimpleName() + " " + fields[i].getName())
            .append(" = ")
            .append(valueToString(values[i]));
        }
        fieldsDump.append("\n");
      }
    }

    StringBuilder variantsDump = new StringBuilder();
    if (variants != null && variants.size() > 0) {
      for (int i = variants.size() - 1; i > -1; i--) {
        variantsDump.append("\t");
        if (i == currentAlternative) {
          variantsDump.append("-->");
        } else {
          variantsDump.append("   ");
        }
        variantsDump.append(variants.get(i))
          .append("\n");
      }
    }
    return new StringBuilder()
      .append(String.format("%-32s", tokenType.getSimpleName() + memberId))
      .append(" @ ")
      .append(location.toString())
      .append("\n")
      .append(members)
      .append(fieldsDump)
      .append(variantsDump)
      .toString();
  }

  private String valueToString(Object value) {
    if (value == null) {
      return "null\n";
    }
    Class type = value.getClass();
    StringBuilder result = new StringBuilder(); 
    if (type.isArray()) {
      result.append("[\n");
      Object[] collection = (Object[]) value;
      for (int i = 0; i < collection.length; i++) {
        String valueString = collection[i] == null ? "null" : collection[i].toString().replaceAll("\n", "\n\t\t\t");
        result.append("\t\t")
          .append(String.format("%-4d", i))
          .append(valueString)
          .append("\n");
      }
      result.append("\t    ]");
    } else {
      result.append(value.toString());
    }
    result.append('\n');
    return result.toString();
  }

  protected boolean isTransient(Field field) {
    return field != null && Modifier.isTransient(field.getModifiers());
  }

  public ParserLocation getLocation() {
    return location;
  }

  public PartialToken<?> getParent() {
    return parent;
  }

  public void discard() {
    if (token instanceof Rule) {
      Rule.Metadata.remove((Rule)token);
    }
  }
}

