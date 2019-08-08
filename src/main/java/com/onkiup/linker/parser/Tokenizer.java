package com.onkiup.linker.parser;

// in 0.2.2:
// - added "C" type parameter
// - bound X to Rule
@FunctionalInterface
public interface Tokenizer<C, X extends Rule<C>> extends Function<StringBuilder, TokenTestResult> {
  public X read(NestingReader reader, C context, PartialToken<X> token);

  @Override
  public TokenTestResult apply(StringBuilder source) {
    return TestResult.recurse();
  }
}

