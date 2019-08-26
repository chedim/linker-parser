package com.onkiup.linker.parser.token;

import java.util.Optional;

/**
 * A special token used to return characters from force-failed tokens
 */
public class FailedToken implements PartialToken, ConsumingToken {

  private PartialToken parent;
  private StringBuilder data;
  private int position;

  public FailedToken(PartialToken parent, StringBuilder data, int position) {
    this.parent = parent;
    this.data = data;
    this.position = position;
  }

  @Override
  public int consumed() {
    return data.length();
  }

  @Override
  public int position() {
    return position;
  }

  @Override 
  public Optional<PartialToken> getParent() {
    return Optional.ofNullable(parent);
  }

  @Override
  public boolean isPopulated() {
    return false;
  }

  @Override
  public Class getTokenType() {
    return String.class;
  }

  @Override
  public String getToken() {
    return data.toString();
  }

  @Override
  public Optional<StringBuilder> pullback() {
    throw new RuntimeException("Not supported");
  }

  @Override
  public Optional<PartialToken> advance(boolean last) {
    return getParent()
      .flatMap(p -> p.advance(last));
  }

  @Override 
  public Optional<StringBuilder> consume(char character, boolean last) {
    return Optional.of(new StringBuilder().append(data).append(character));
  }
}

