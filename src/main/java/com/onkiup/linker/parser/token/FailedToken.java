package com.onkiup.linker.parser.token;

import java.util.Optional;

import com.onkiup.linker.parser.ParserLocation;

/**
 * A special token used to return characters from force-failed tokens
 */
public class FailedToken implements PartialToken, ConsumingToken {

  private PartialToken parent;
  private StringBuilder data;
  private ParserLocation location;
  private ParserLocation end;

  public FailedToken(PartialToken parent, StringBuilder data, ParserLocation location) {
    this.parent = parent;
    this.location = location;
    this.data = data;
    int line = location.line(), column = location.column();
    for(int i = 0; i < data.length(); i++) {
      char chr = data.charAt(i);
      if(chr == '\n') {
        line++;
        column = 0;
      } else {
        column++;
      }
    }
    this.end = new ParserLocation(
        location.name(),
        location.position() + data.length(),
        location.line() + line,
        column
    );
  }

  @Override
  public int consumed() {
    return data.length();
  }

  @Override
  public ParserLocation location() {
    return location;
  }

  @Override
  public ParserLocation end() {
    return end;
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
  
  @Override
  public StringBuilder source() {
    return new StringBuilder(data.toString());
  }

  @Override
  public PartialToken expected() {
    return parent.expected();
  }

  @Override
  public String toString() {
    return new StringBuilder()
      .append("'")
      .append(data.length() > 10 ? data.substring(0, 5) + "<..>" + data.substring(data.length() - 6) : data)
      .append("'")
      .append(" <-- FailedToken@")
      .append(location)
      .append(" --> ")
      .append(parent)
      .toString();
  }
}

