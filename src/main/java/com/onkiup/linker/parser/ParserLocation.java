package com.onkiup.linker.parser;

public class ParserLocation {
  private final String source;
  private final int line, row;

  public ParserLocation(String source, int line, int row) {
    this.source = source;
    this.line = line;
    this.row = row;
  }

  public String source() {
    return source;
  }

  public int line() {
    return line;
  }

  public int row() {
    return row;
  }
}

