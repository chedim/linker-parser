package com.onkiup.linker.parser;

public class SyntaxError extends Exception {
  public SyntaxError(String message, ParserState state) {
    super(message + '\n' + state);
  }
}

