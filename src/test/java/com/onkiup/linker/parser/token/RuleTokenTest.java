package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TokenGrammar;
import com.onkiup.linker.parser.annotation.CaptureLimit;
import com.onkiup.linker.parser.annotation.CapturePattern;
import com.onkiup.linker.parser.annotation.IgnoreCharacters;

import static org.junit.Assert.*;

public class RuleTokenTest {
  private static final Logger logger = LoggerFactory.getLogger(RuleTokenTest.class);

  public static class TestRule implements Rule {
    private String first;
    private String second;
  }

//  @Test
  public void testAdvance() throws Exception {
    PartialToken child = Mockito.mock(PartialToken.class);
    Mockito.when(child.isPopulated()).thenReturn(true);
    Mockito.when(child.source()).thenReturn(new StringBuilder("123"));
    PowerMockito.when(PartialToken.forField(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(child);
    RuleToken token = new RuleToken(null, TestRule.class, ParserLocation.ZERO);

    assertSame(child, token.advance(false).get());
    assertSame(child, token.advance(false).get());
    assertFalse(token.advance(false).isPresent());
    PowerMockito.verifyStatic(PartialToken.class, Mockito.times(2));
    PartialToken.forField(Mockito.eq(token), Mockito.any(Field.class), Mockito.any());

    assertEquals("123123", token.source().toString());
  }

//  @Test
  public void testRollback() throws Exception {
   PartialToken child = Mockito.mock(PartialToken.class);
   Mockito.when(child.isPopulated()).thenReturn(true);
   Mockito.when(child.pullback()).thenReturn(Optional.of(new StringBuilder("a")));
   PowerMockito.when(PartialToken.forField(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(child);
   PartialToken parent = Mockito.mock(PartialToken.class);
   Mockito.when(parent.pushback(false)).thenReturn(Optional.of(new StringBuilder("aa")));

   RuleToken token = new RuleToken(parent, TestRule.class, ParserLocation.ZERO);
   token.advance(false);
   token.advance(false);

   Optional<StringBuilder> rollbacked = token.pushback(false);
   StringBuilder result = rollbacked.get();
   assertEquals("aa", result.toString());
  }
}
