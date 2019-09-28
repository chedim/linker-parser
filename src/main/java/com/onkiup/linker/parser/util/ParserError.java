package com.onkiup.linker.parser.util;

import java.util.Optional;

import com.onkiup.linker.parser.token.PartialToken;

public class ParserError extends RuntimeException {

  private PartialToken source;
  
  public ParserError(String msg, Optional<PartialToken<?>> source) {
    this(msg, source.orElse(null));
  }

  public ParserError(String msg, PartialToken source) {
    super(msg);
    this.source = source;
  }

  public ParserError(String msg, Optional<PartialToken<?>> source, Throwable cause) {
    this(msg, source.orElse(null), cause);
  }

  public ParserError(String msg, PartialToken source, Throwable cause) {
    super(msg, cause);
    this.source = source;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder("Parser error at position ");
    result.append(source == null ? "<unknown>" : source.position())
      .append(": ")
      .append(getMessage())
      .append("\n");

    PartialToken parent = source;
    while(parent != null) {
      result.append("\t")
        .append(parent.toString())
        .append("\n");
      parent = (PartialToken) parent.parent().orElse(null);
    }
    return result.toString();
  }

}

