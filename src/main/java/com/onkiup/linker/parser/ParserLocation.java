package com.onkiup.linker.parser;

public class ParserLocation {
  private final String sourceName;
  private final String source;
  private final int line, row;

  public ParserLocation(String sourceName, String source, int line, int row) {
    this.sourceName = sourceName;
    this.source = source;
    this.line = line;
    this.row = row;
  }

  public String sourceName() {
    return sourceName;
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

  @Override
  public String toString() {
    return new StringBuilder().append(sourceName == null ? "" : sourceName + ':')
      .append(line)
      .append(':')
      .append(row)
      .toString();
  }
}

