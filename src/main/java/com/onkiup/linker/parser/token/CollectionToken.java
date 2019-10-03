package com.onkiup.linker.parser.token;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.annotation.CaptureLimit;
import com.onkiup.linker.parser.util.LoggerLayout;
import com.onkiup.linker.parser.util.ParserError;

public class CollectionToken<X> extends AbstractToken<X> implements CompoundToken<X> {
  private Class<X> fieldType;
  private Class memberType;
  private LinkedList<PartialToken> children = new LinkedList<>();
  private CaptureLimit captureLimit;
  private ParserLocation lastTokenEnd;
  private int nextMember = 0;

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
    if (children.size() == 0) {
      throw new RuntimeException("OnChildPopulated called when there is no child!");
    }
    PartialToken<?> current = children.peekLast();
    if (current.isMetaToken()) {
      addMetaToken(current.token());
      children.pollLast();
      return;
    }
    log("Populated collection token #{}: {}", children.size(), current.tag());
    lastTokenEnd = current.end();
    if (captureLimit != null && children.size() >= captureLimit.max()) {
      onPopulated(lastTokenEnd);
    }
  }

  @Override
  public void atEnd() {
    log("Force-populating...");
    if (captureLimit == null || children.size() >= captureLimit.min()) {
      onPopulated(lastTokenEnd);
    } else {
      onFail();
    }
  }

  @Override
  public void onChildFailed() {
    if (children.size() == 0) {
      throw new ParserError("No child is currently populated yet onChildFailed was called", this);
    }

    children.pollLast();
    lastTokenEnd = children.size() > 0 ? children.peekLast().end() : location();
    int size = children.size();
    if (captureLimit != null && size < captureLimit.min()) {
      log("Child failed and collection is underpopulated -- failing the whole collection");
      if (!alternativesLeft()) {
        onFail();
      } else {
        log("Not failing -- have some alternatives left");
      }
    } else {
      log("Child failed and collection has enough elements (or no lower limit) -- marking collection as populated");
      onPopulated(children.size() == 0 ? location() : lastTokenEnd);
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
    return fieldType.getName() + "[]("+position()+")";
  }

  @Override
  public String toString() {
    ParserLocation location = location();
    return String.format(
        "%50.50s || %s[%d] (%d:%d -- %d - %d)",
        head(50),
        fieldType.getName(),
        children.size(),
        location.line(),
        location.column(),
        location.position(),
        end().position()
    );
  }

  @Override
  public ParserLocation end() {
    return isFailed() ? location() : children.size() > 0 ? children.peekLast().end() : lastTokenEnd;
  }

  @Override
  public Optional<PartialToken<?>> nextChild() {
    if (isFailed() || isPopulated()) {
      return Optional.empty();
    }

    PartialToken<?> current = null;
    if (captureLimit == null || captureLimit.max() > children.size()) {
      if (nextMember == children.size()) {
        log("creating partial token for member#{}", children.size());
        current = PartialToken.forField(this, targetField().orElse(null), memberType, lastTokenEnd);
        children.add(current);
      } else if (nextMember < children.size()) {
        current = children.get(nextMember);
      }
      nextMember++;
      log("nextChild = [{}]{}", children.size(), current.tag());
      return Optional.of(current);
    }
    return Optional.empty();
  }

  @Override
  public PartialToken[] children() {
    return children.toArray(new PartialToken[children.size()]);
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
    nextMember = newIndex;
    log("next child set to {}/{} ({})", newIndex, children.size(), children.get(newIndex));
  }

  @Override
  public void children(PartialToken<?>[] children) {
    this.children = new LinkedList<>(Arrays.asList(children));
  }

  @Override
  public boolean alternativesLeft() {
    for (int i = children.size() - 1; i > -1; i--) {
      PartialToken<?> child = children.get(i);
      log("getting alternatives from [{}]{}", i, child.tag());
      if (child.alternativesLeft()) {
        log("found alternatives at [{}]{}", i, child.tag());
        return true;
      }
    }

    return false;
  }

  @Override
  public CharSequence dumpTree(int offset, CharSequence prefix, CharSequence childPrefix, Function<PartialToken<?>, CharSequence> formatter) {
    final int childOffset = offset + 1;
    String insideFormat = "%s ├─%s #%s : %s";
    String lastFormat = "%s └─%s #%s : %s";
    StringBuilder result = new StringBuilder(super.dumpTree(offset, prefix, childPrefix, formatter));
    if (!isPopulated()) {
      int last = children.size() - 1;
      for (int i = 0; i < children.size(); i++) {
        PartialToken<?> child = children.get(i);
        String format = i == last ? lastFormat : insideFormat;
        if (child == null) {
          result.append(String.format(format, childPrefix, "[N]", i, null));
          result.append('\n');
        } else if (child.isPopulated()) {
          result.append(child.dumpTree(childOffset, String.format(format, childPrefix, "[+]", i, ""),
              childPrefix + " │", formatter));
        } else if (child.isFailed()) {
          result.append(child.dumpTree(childOffset, String.format(format, childPrefix, "[F]", i, ""),
              childPrefix + " │", formatter));
        } else {
          result.append(child.dumpTree(childOffset, String.format(format, childPrefix, ">>>", i, ""),
              childPrefix + " │", formatter));
        }
      }
    }
    return result;
  }
}
