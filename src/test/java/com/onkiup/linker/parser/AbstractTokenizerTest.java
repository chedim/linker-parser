package com.onkiup.linker.parser;

import static org.junit.Assert.*;
import org.junit.Test;

public class AbstractTokenizerTest {

  @Test
  public void testTokenizeWithMatchers() {
    AbstractTokenizer tokenizer = Mockito.mock(AbstractTokenizer.class);
    StringReader source = new StringReader("test1 test2");

    Mockito.when(tokenizer.tokenize(Mockito.any(), Mockito.any())).thenCallRealMethod();
    Mockito.when(tokenizer.getVariants()).thenReturn(new Class[] {String.class});


  }

}

