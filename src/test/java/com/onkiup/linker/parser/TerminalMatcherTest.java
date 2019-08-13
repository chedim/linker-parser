package com.onkiup.linker.parser;

import org.junit.Test;

import static org.junit.Assert.*;

public class TerminalMatcherTest {

  @Test 
  public void testMatching() {
    StringBuilder source = new StringBuilder("test"); 
    TerminalMatcher matcher = new TerminalMatcher("test it");

    assertTrue(matcher.apply(source).isMatchContinue());
    source.append(" it");
    assertTrue(matcher.apply(source).isMatch());
  }

}

