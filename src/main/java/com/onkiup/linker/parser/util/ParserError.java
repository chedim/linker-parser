package com.onkiup.linker.parser.util;

import com.onkiup.linker.parser.token.PartialToken;

public class ParserError extends RuntimeException {

  private PartialToken source;

  public ParserError(String msg, PartialToken source) {
    super(message);
    this.source = source;
  }

  public ParserError(String msg, PartialToken source, Throwable cause) {
    super(message, cause);
    this.source = source;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder("Parser error at position ");
    result.append(source.position())
      .append(": ")
      .append(getMessage())
      .append("\n");

    PartialToken parent = source;
    while(parent != null) {
      result.append("\t")
        .append(parent.toString())
        .append("\n");
      parent = (PartialToken) parent.getParent().orElse(null);
    }
    return result.toString();
  }

}

