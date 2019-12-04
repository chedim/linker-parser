package com.onkiup.linker.parser.token;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Function;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.annotation.CaptureLimit;
import com.onkiup.linker.parser.util.ParserError;

/**
 * Token that is used to populate array fields
 * @param <X> array class of the result token
 */
public class CollectionToken<X> extends AbstractToken<X> implements CompoundToken<X>, Serializable {
  /**
   * the type of the resulting token
   */
  private Class<X> fieldType;
  /**
   * the type of array members
   */
  private Class memberType;
  /**
   * tokens that represent matched array members
   */
  private LinkedList<PartialToken> children = new LinkedList<>();
  /**
   * maximum number of array members to match
   */
  private CaptureLimit captureLimit;
  /**
   * the position immediately after the end of the last matched token (or CollectionToken's location if no tokens were matched)
   */
  private ParserLocation lastTokenEnd;
  /**
   * index of the next member to match
   */
  private int nextMember = 0;

  /**
   * Main constructor
   * @param parent parent token
   * @param field field for which this token is constructed
   * @param tokenType type of the resulting array
   * @param location location of the token in parser's buffer
   */
  public CollectionToken(CompoundToken parent, int childIndex, Field field, Class<X> tokenType, ParserLocation location) {
    super(parent, childIndex, field, location);
    lastTokenEnd = location;
    this.fieldType = tokenType;
    this.memberType = fieldType.getComponentType();
    if (field.isAnnotationPresent(CaptureLimit.class)) {
      captureLimit = field.getAnnotation(CaptureLimit.class);
    }
  }

  /**
   * Handler that is invoked every time an array member is populated
   */
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

  /**
   * Callback that handles end-of-input situation by marking the array populated or failed (if number of children is smaller than configured by {@link CaptureLimit} annotation on the target field)
   */
  @Override
  public void atEnd() {
    log("Force-populating...");
    if (captureLimit == null || children.size() >= captureLimit.min()) {
      onPopulated(lastTokenEnd);
    } else {
      onFail();
    }
  }

  /**
   * Callback that handles member token matching failure by marking the array populated or failed (if number of children is smaller than configured by {@link CaptureLimit} annotation on the target field)
   */
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

  /**
   * @return the type of the resulting array (not it members!)
   */
  @Override
  public Class<X> tokenType () {
    return fieldType;
  }

  /**
   * @return matched token
   */
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

  /**
   * Creates an array of elements of given type
   * @param memberType type of the members of the resulting array
   * @param size the size of the array
   * @return created array
   */
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
        current = PartialToken.forField(this, children.size(), targetField().orElse(null), memberType, lastTokenEnd);
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

  @Override
  public int childCount() {
    return children.size();
  }

  @Override
  public Optional<PartialToken<?>> child(int position) {
    if (position < 0 || position >= children.size()) {
      return Optional.empty();
    }
    return Optional.ofNullable(children.get(position));
  }
}
