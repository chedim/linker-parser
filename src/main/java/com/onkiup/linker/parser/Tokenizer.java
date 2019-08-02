package com.onkiup.linker.parser;

public interface  Tokenizer<X> {
  public X tokenize(NestingReader reader, boolean evaluate);
}

