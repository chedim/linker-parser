package com.onkiup.linker.parser;

public class EvaluationError extends RuntimeException {

  public EvaluationError(PartialToken token, Object context, Exception cause) {
    super("Failed to evaluate token " + token.getTokenType(), cause);
  }
}

