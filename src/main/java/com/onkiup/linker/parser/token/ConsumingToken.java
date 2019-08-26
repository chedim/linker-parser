package com.onkiup.linker.parser.token;

import java.util.Optional;

public interface ConsumingToken<X> extends PartialToken<X> {
  /**
   * Attempts to consume next character
   * @returns null if character was consumed, otherwise returns a StringBuilder with failed characters
   */
  Optional<StringBuilder> consume(char character, boolean last);
}

