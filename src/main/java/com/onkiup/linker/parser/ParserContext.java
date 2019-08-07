package com.onkiup.linker.parser;

public class ParserContext<C> {
  private String source;
  private int line, row;
  private C scriptContext;

  public ParserContext(String source, C scriptContext) {
    this.source = source;
    this.scriptContext = scriptContext;
  }

  public ParserLocation location() {
    return new ParserLocation(source, line, row);
  }

  public void advance(boolean line) {
    if (line) {
      this.line++;
      this.row = 0;
    } else {
      this.row++;
    }
  }

  public C scriptContext() {
    return scriptContext;
  }

}

