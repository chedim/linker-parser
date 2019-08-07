package com.onkiup.linker.parser;

import static org.junit.Assert.*;
import org.junit.Test;

public class ArrayMatcherTest {

  @Test
  public void testApply() {
    ArrayMatcher matcher = new ArrayMatcher(null);
    TokenTestResult result = matcher.apply(null);

    assertEquals(TestResult.RECURSE, result.getResult());
  }

}

