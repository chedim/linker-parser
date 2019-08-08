package com.onkiup.linker.parser;

public class AbstractTokenizer<C, X> implements Tokenizer<C, X>, TokenMatcher<X> {

  protected abstract Set<Tokenizer<C, ? extends X>> getVariants();

  @Override
  public X tokenize(NestingReader source, C context, PartialToken<C, X> partial) {
    Set<Tokenizer<C, ? extends X>> variants = getVariants();
 
    Map<Class<? extends X>, TokenTestResult> prevMatches = null;
    Map<Class<? extends X>, StringBuilder> buffers = new HashMap<>();
    int nextChar;


    return null;
  }

  @Override
  public TokenTestResult apply(StringBuilder buffer) {
    return TestResult.recurse();
  }
}

