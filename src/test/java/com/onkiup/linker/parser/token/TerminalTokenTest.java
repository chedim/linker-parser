package com.onkiup.linker.parser.token;

import org.junit.Test;
import org.mockito.Mockito;

import com.onkiup.linker.parser.TestResult;
import com.onkiup.linker.parser.TokenMatcher;

import static org.junit.Assert.*;

public class TerminalTokenTest {

  @Test
  public void testConsume() {
    TokenMatcher matcher = Mockito.mock(TokenMatcher.class);
    Mockito.when(matcher.apply(Mockito.any())).thenAnswer(invocation -> {
      StringBuilder buffer = (StringBuilder) invocation.getArgument(0);
      if ("test".equals(buffer.toString())) {
        return TestResult.match(4, "test");
      } else if ("test".startsWith(buffer.toString())) {
        return TestResult.matchContinue(buffer.length(), buffer.toString());
      }
      return TestResult.fail();
    });

    TerminalToken token = new TerminalToken(null, matcher);
    assertFalse(token.consume('t', false).isPresent());
    assertFalse(token.consume('e', false).isPresent());
    assertFalse(token.consume('s', false).isPresent());
    assertEquals("", token.consume('t', false).get().toString());
    assertEquals("X", token.consume('X', true).get().toString());
  }

  @Test
  public void testIgnoreCharacters() {
    TokenMatcher matcher = Mockito.mock(TokenMatcher.class);
    Mockito.when(matcher.apply(Mockito.any())).thenReturn(TestResult.match(1, "X"));
    PartialToken parent = Mockito.mock(PartialToken.class);
    Mockito.when(parent.getIgnoredCharacters()).thenReturn(" ");

    TerminalToken token = new TerminalToken(parent, matcher);
    assertFalse(token.consume(' ', false).isPresent());
    assertEquals("", token.consume('X', false).get().toString());
    assertEquals("Y", token.consume('Y', true).get().toString());
  }

}

