package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.SyntaxError;
import com.onkiup.linker.parser.annotation.CaptureLimit;
import com.onkiup.linker.parser.util.ParserError;

public class CollectionToken<X> implements PartialToken<X> {
  private static final Logger logger = LoggerFactory.getLogger(CollectionToken.class);

  private PartialToken<? extends Rule> parent;
  private Field field;
  private Class<X> fieldType;
  private Class memberType;
  private LinkedList<PartialToken> children = new LinkedList<>();
  private PartialToken current;
  private CaptureLimit captureLimit;
  private boolean populated = false;
  private final ParserLocation location;
  private ParserLocation lastTokenEnd;

  public CollectionToken(PartialToken<? extends Rule> parent, Field field, ParserLocation location) {
    this.parent = parent;
    this.field = field;
    this.location = location;
    lastTokenEnd = location;
    this.fieldType = (Class<X>) field.getType(); 
    this.memberType = fieldType.getComponentType();
    if (field.isAnnotationPresent(CaptureLimit.class)) {
      captureLimit = field.getAnnotation(CaptureLimit.class);
    }
  }

  @Override
  public Optional<StringBuilder> pushback(boolean force) {
    int size = children.size();

    if (captureLimit != null && size <= captureLimit.min()) {
      logger.debug("failed to satisfy all requirements --> passing rollback call to the parent");
      return parent.pushback(false);
    }

    logger.info("Pulling back failed (last) member {} and marking collection token as populated: {}", current, this);
    populated = true;
    PartialToken lastMember = current;
    return lastMember.pullback();
  }

  @Override
  public Optional<StringBuilder> pullback() {
    logger.debug("Pulling {} back", this);
    StringBuilder result = new StringBuilder();
    children.stream()
      .map(PartialToken::pullback)
      .forEach(o -> o.ifPresent(result::append));

    return Optional.of(result);
  }

  @Override
  public Optional<PartialToken> advance(boolean force) throws SyntaxError {
    logger.debug("Advancing {}", this);
    int size = children.size();
    if (current != null && !current.isPopulated()) {
      logger.debug("Last collection token failed");
      if (captureLimit == null || (size >= captureLimit.min() || size <= captureLimit.max())) {
        logger.debug("Marking collection as populated");
        populated = true;
      }
    } else if (current != null) {
      children.add(current);
      lastTokenEnd = current.end();
      populated = (captureLimit == null) ? false : ++size == captureLimit.max();
      logger.info("Appended token {} to {}; populated: {}", current, this, populated);
    }

    if (force) {
      logger.info("Force-populating collection token");
      populated = true;
    }

    if (populated) {
      return parent == null ? Optional.empty() : parent.advance(force);
    } else {
      current = PartialToken.forClass(this, memberType, lastTokenEnd);
      logger.debug("Advancing to next collection member: {}", current);
      return Optional.of(current);
    }
  }

  @Override
  public String getIgnoredCharacters() {
    if (parent != null) {
      return parent.getIgnoredCharacters();
    }
    return "";
  }

  @Override
  public Optional<PartialToken> getParent() {
    return Optional.ofNullable(parent);
  }

  @Override
  public boolean isPopulated() {
    if (captureLimit != null) {
      int size = children.size();
      logger.debug("Testing if collection is populated (size: {}; min: {})", size, captureLimit.min());
      return size >= captureLimit.min();
    }
    return populated;
  }

  @Override
  public Class<X> getTokenType () {
    return fieldType;
  }

  @Override
  public X getToken() {
    if (!populated) {
      throw new IllegalStateException("Not populated");
    }

    return (X) children.stream()
      .map(PartialToken::getToken)
      .toArray();
  }

  @Override
  public int consumed() {
    return lastTokenEnd == null ? 0 : lastTokenEnd.position() - position();
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder("'")
      .append(tail(10).replaceAll("\n", "\\n"))
      .append("' <-- Collection(")
      .append(memberType.getSimpleName())
      .append(")@[")
      .append(location.position())
      .append(" - ")
      .append(end().position())
      .append("]: ")
      .append(children.size())
      .append("\n");

    for(int i = children.size() - 1; i > -1; i--) {
      PartialToken child = children.get(i);
      result.append("\t")
        .append(String.format("%3d: ", i))
        .append(child == null ? null : child.toString().replaceAll("\n",  "\n\t\t"))
        .append("\n");
    }

    return result.toString();
  }

  @Override
  public ParserLocation location() {
    if (children.size() > 0) {
      return children.get(0).location();
    }
    return location;
  }

  @Override
  public ParserLocation end() {
    return lastTokenEnd == null ? location : lastTokenEnd;
  }

  @Override
  public StringBuilder source() {
    StringBuilder result = new StringBuilder();
    
    for (int i = 0; i < children.size(); i++) {
      result.append(children.get(i).source());
    }
    return result;
  }

  @Override
  public PartialToken[] getChildren() {
    PartialToken[] result = new PartialToken[children.size()];
    return children.toArray(result);
  }


  @Override
  public PartialToken expected() {
    return current.expected();
  }
  
  @Override
  public String tag() {
    return "Collection<" + memberType.getSimpleName() + ">";
  }
}
