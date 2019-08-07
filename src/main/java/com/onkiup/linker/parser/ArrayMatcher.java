package com.onkiup.linker.parser;

import java.io.Reader;

// in 0.2.2:
// - added "C" parameter
// - bound "X"
public class ArrayMatcher<C, X extends Rule<C>> implements TokenMatcher, Tokenizer<C, X>{

  private Tokenizer<C, X> elementMatcher;

  public ArrayMatcher(Tokenizer<C, X> elementMatcher) {
    this.elementMatcher = elementMatcher;
  }

  @Override
  public TokenTestResult apply(StringBuilder stringBuilder) {
    return TestResult.recurse();
  }

  @Override
  public X tokenize(NestingReader source, C context) {
    return null;
  }
}

