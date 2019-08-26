package com.onkiup.linker.parser;

import com.onkiup.linker.parser.token.PartialToken;

public class EvaluationError extends RuntimeException {

  public EvaluationError(PartialToken token, Object context, Exception cause) {
    super("Failed to evaluate token " + token.getTokenType(), cause);
  }
}

