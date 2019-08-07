package com.onkiup.linker.parser;

import java.io.Reader;

// in 0.2.2:
// - added "C" parameter
// - bound "X"
public class ArrayMatcher<C, X extends Rule<C>> extends AbstractTokenizer<C, X[]> {

  private TokenMatcher<X> matcher;
  private CaptureLimit limit;

  public ArrayMatcher(TokenMatcher<X> matcher, CaptureLimit limit) {
    this.matcher = matcher;
    this.limit = limit;
  }

  @Override
  public X tokenize(NestingReader source, C context) {
    StringBuilder buffer = new StringBuilder();
    int nextChar;


    try {
      ArrayList<X> result = 
    } catch (Exception e) {
    
    }
  }
}

