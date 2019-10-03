package com.onkiup.linker.parser;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParserLocation {

  private static final Logger logger = LoggerFactory.getLogger(ParserLocation.class);
  public static ParserLocation ZERO = new ParserLocation("unknown", 0,0,0);

  private final int line, column, position;
  private final String name;

  public static ParserLocation endOf(CharSequence text) {
    int lines = 0;
    int column = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') {
        lines++;
        column = 0;
      } else {
        column++;
      }
    }

    return new ParserLocation(null, text.length(), lines, column);
  }

  public ParserLocation(String name, int position, int line, int column) {
    if (position < 0) {
      throw new IllegalArgumentException("Position cannot be negative");
    }

    if (line < 0) {
      throw new IllegalArgumentException("Line cannot be negative");
    }

    if (column < 0) {
      throw new IllegalArgumentException("Column cannot be negative");
    }

    this.name = name;
    this.position = position;
    this.line = line;
    this.column = column;
  }

  public String name() {
    return name;
  }

  public int position() {
    return position;
  }

  public int line() {
    return line;
  }

  public int column() {
    return column;
  }

  @Override
  public String toString() {
    return new StringBuilder()
      .append(name)
      .append(" - ")
      .append(line)
      .append(':')
      .append(column)
      .toString();
  }


  public ParserLocation advance(CharSequence source) {
    int position = this.position + source.length();
    int line = this.line;
    int column = this.column;
    for (int i = 0; i < source.length(); i++) {
      if (source.charAt(i) == '\n') {
        line++;
        column = 0;
      } else {
        column++;
      }
    }

    ParserLocation result = new ParserLocation(name, position, line, column);
    logger.debug("Advanced from {} to {} using chars: '{}'", this, result, source);
    return result;
  }

  public ParserLocation advance(char character) {
    if (character < 0) {
      return this;
    }
    int column = this.column + 1;
    int line = this.line;
    if (character == '\n') {
      line++;
      column = 0;
    }
    return new ParserLocation(name, position + 1, line, column);
  }

  public ParserLocation add(ParserLocation another) {
    if (another.name() != null && !Objects.equals(name(), another.name())) {
      throw new IllegalArgumentException("Unable to add parser location with a different name");
    }
    int anotherLines = another.line();
    int resultLine = line + anotherLines;
    int resultColumn = anotherLines == 0 ? column + another.column() : another.column();
    return new ParserLocation(name, position + another.position(), resultLine, resultColumn);
  }
}

