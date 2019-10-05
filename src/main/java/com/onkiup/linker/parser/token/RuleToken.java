package com.onkiup.linker.parser.token;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.annotation.IgnoreCharacters;
import com.onkiup.linker.parser.util.LoggerLayout;

/**
 * PartialToken used to populate concrete Rule instances
 * @param <X>
 */
public class RuleToken<X extends Rule> extends AbstractToken<X> implements CompoundToken<X>, Rotatable, Serializable {
  private X token;
  private Class<X> tokenType;
  private Field[] fields;
  private PartialToken[] values;
  private int nextChild = 0;
  private String ignoreCharacters = ""; 
  private boolean rotated = false;
  private transient ParserLocation lastTokenEnd;

  public RuleToken(CompoundToken parent, Field field, Class<X> type, ParserLocation location) {
    super(parent, field, location);
    this.tokenType = type;
    this.lastTokenEnd = location;

    try {
      this.token = type.newInstance();
      Rule.Metadata.metadata(token, this);
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
    return Optional.ofNullable(token);
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
      log("No next child (nextChild = {}; fields = {})", nextChild, fields.length);
      return Optional.empty();
    }
    if (values[nextChild] == null || values[nextChild].isFailed() || values[nextChild].isPopulated()) {
      Field childField = fields[nextChild];
      log("Creating partial token for child#{} at position {}", nextChild, lastTokenEnd.position());
      values[nextChild] = PartialToken.forField(this, childField, lastTokenEnd);
    }
    log("nextChild#{} = {}", nextChild, values[nextChild].tag());
    return Optional.of(values[nextChild++]);
  }

  @Override
  public void onChildPopulated() {
    PartialToken<?> child = values[nextChild - 1];

    if (child.isMetaToken()) {
      // woopsie...
      addMetaToken(child.token());
      /* TODO: handle metatokens properly   r
      values[--nextChild] = null;
      return;
       */
    }

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
        log("Optional last child failed -- marking as populated");
        onPopulated(lastTokenEnd);
      } else {
        log ("Ignoring optional child failure");
      }
    } else if (!alternativesLeft()) {
      onFail();
    } else {
      log("not failing -- alternatives left");
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
    return tokenType.getName() + "(" + position() + ")";
  }

  @Override
  public void atEnd() {
    log("Trying to force-populate...");
    for (int i = Math.max(0, nextChild - 1); i < fields.length; i++) {
      if (!PartialToken.isOptional(this, fields[i])) {
        if (values[i] == null || !values[i].isPopulated()) {
          onFail();
          return;
        }
      }
    }
    onPopulated(lastTokenEnd);
  }

  @Override
  public void onPopulated(ParserLocation end) {
    super.onPopulated(end);
    try {
      token.onPopulated();
    } catch (Throwable e) {
      error("Failed to reevaluate on population", e);
    }
  }

  @Override
  public void onFail() {
    super.onFail();
    try {
      token.reevaluate();
    } catch (Throwable e) {
      error("Failed to reevaluate on failure", e);
    }
  }

  @Override
  public String toString() {
    ParserLocation location = location();
    return String.format(
        "%50.50s || %s (%d:%d -- %d - %d)",
        head(50),
        tokenType.getName() + (nextChild > 0 ? ">>" + (fields[nextChild - 1].getName()) : ""),
        location.line(),
        location.column(),
        location.position(),
        end().position()
    );
  }

  @Override
  public ParserLocation end() {
    return isFailed() ?
        location() :
        nextChild > 0 &&values[nextChild - 1] != null ?
            values[nextChild -1].end() :
            lastTokenEnd;
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
    log("next child set to {}/{}", newIndex, fields.length - 1);
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
  public CharSequence dumpTree(int offset, CharSequence prefix, CharSequence childPrefix, Function<PartialToken<?>, CharSequence> formatter) {
    final int childOffset = offset + 1;
    String insideFormat = "%s ├─%s %s : %s";
    String lastFormat = "%s └─%s %s : %s";

    StringBuilder result = new StringBuilder(super.dumpTree(offset, prefix, childPrefix, formatter));
    if (!isPopulated()) {
      for (int i = 0; i <= nextChild; i++) {
        if (i < fields.length) {
          boolean nextToLast = i == fields.length - 2 || i == nextChild - 1;
          boolean last = i == fields.length - 1 || i == nextChild || (nextToLast && values[i + 1] == null);
          String format = i == nextChild ? lastFormat : insideFormat;
          PartialToken<?> child = values[i];
          String fieldName = fields[i].getName();
          if (child == null) {
            result.append(String.format(format, childPrefix, "[N]", fieldName, null));
            result.append('\n');
          } else if (child.isFailed()) {
            result.append(child.dumpTree(childOffset, String.format(format, childPrefix, "[F]", fieldName, ""),
                childPrefix + " │", formatter));
          } else if (child.isPopulated()) {
            result.append(child.dumpTree(childOffset, String.format(format, childPrefix, "[+]", fieldName, ""),
                childPrefix + " │", formatter));
          } else {
            result.append(child.dumpTree(childOffset, String.format(format, childPrefix, ">>>", fieldName, ""),
                childPrefix + " │", formatter));
          }
        }
      }
    }
    return result;
  }
}

