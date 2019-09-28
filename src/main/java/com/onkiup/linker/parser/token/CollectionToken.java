package com.onkiup.linker.parser.token;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Optional;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.annotation.CaptureLimit;

public class CollectionToken<X> extends AbstractToken<X> implements CompoundToken<X> {
  private Class<X> fieldType;
  private Class memberType;
  private LinkedList<PartialToken> children = new LinkedList<>();
  private PartialToken current;
  private CaptureLimit captureLimit;
  private ParserLocation lastTokenEnd;

  public CollectionToken(CompoundToken parent, Field field, Class<X> tokenType, ParserLocation location) {
    super(parent, field, location);
    lastTokenEnd = location;
    this.fieldType = tokenType;
    this.memberType = fieldType.getComponentType();
    if (field.isAnnotationPresent(CaptureLimit.class)) {
      captureLimit = field.getAnnotation(CaptureLimit.class);
    }
  }

  @Override
  public void onChildPopulated() {
    if (current == null) {
      throw new RuntimeException("OnChildPopulated called when there is no child!");
    }
    children.add(current);
    current = null;
    if (captureLimit != null && children.size() >= captureLimit.max()) {
      onPopulated(current.end());
    }
  }

  @Override
  public void onChildFailed() {
    if (current == null) {
      throw new IllegalStateException("No child is currently populated yet onChildFailed was called");
    }

    int size = children.size();
    if (captureLimit != null && size < captureLimit.min()) {
      log("Child failed and collection is underpopulated -- failing the whole collection");
      onFail();
    } else {
      log("Child failed and collection has enough elements (or no lower limit) -- marking collection as populated");
      onPopulated(lastTokenEnd);
    }
  }

  @Override
  public Class<X> tokenType () {
    return fieldType;
  }

  @Override
  public Optional<X> token() {
    if (!isPopulated()) {
      return Optional.empty();
    }

    return Optional.of((X) children.stream()
      .map(PartialToken::token)
        .map(o -> o.orElse(null))
      .toArray(size -> newArray(memberType, size)));
  }

  private static final <M> M[] newArray(Class<M> memberType, int size) {
    return (M[]) Array.newInstance(memberType, size);
  }

  @Override
  public String tag() {
    return fieldType.getName() + "[]";
  }

  @Override
  public String toString() {
    return String.format("%-50.50s || %s[%d] (position: %d)", tail(50), fieldType.getName(), children.size(), position());
  }

  @Override
  public CharSequence source() {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < children.size(); i++) {
      result.append(children.get(i).source());
    }
    if (current != null) {
      result.append(current.source());
    }
    return result;
  }

  @Override
  public Optional<PartialToken<?>> nextChild() {
    if (captureLimit == null || captureLimit.max() > children.size()) {
      return Optional.of(current = PartialToken.forField(this, targetField().orElse(null), memberType, lastTokenEnd));
    }
    return Optional.empty();
  }

  @Override
  public PartialToken[] children() {
    PartialToken[] result;
    if (current != null) {
      result = new PartialToken[children.size() + 1];
      result = children.toArray(result);
      result[result.length - 1] = current;
    } else {
      result = new PartialToken[children.size()];
      result = children.toArray(result);
    }
    return result;
  }

  @Override
  public int unfilledChildren() {
    if (isPopulated()) {
      return 0;
    }
    if (captureLimit == null) {
      return 1;
    }

    return captureLimit.max() - children.size();
  }

  @Override
  public int currentChild() {
    return children.size() - 1;
  }

  @Override
  public void nextChild(int newIndex) {
    children = new LinkedList<>(children.subList(0, newIndex));
    current = children.peekLast();
  }

  @Override
  public void children(PartialToken<?>[] children) {
    this.children = new LinkedList<>(Arrays.asList(children));
    current = null;
  }

  @Override
  public int alternativesLeft() {
    return current == null ? 0 : current.alternativesLeft();
  }
}
