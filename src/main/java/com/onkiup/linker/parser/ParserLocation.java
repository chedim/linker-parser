package com.onkiup.linker.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParserLocation {

  private static final Logger logger = LoggerFactory.getLogger(ParserLocation.class);
  public static ParserLocation ZERO = new ParserLocation("unknown", 0,0,0);

  private final int line, column, position;
  private final String name;

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
}

