package com.onkiup.linker.parser;

// in 0.2.2:
// - added "C" type parameter
// - bound X to Rule
public interface Tokenizer<C, X extends Rule<C>> {
  public X tokenize(NestingReader reader, C context);
}

