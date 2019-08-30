package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TokenGrammar;
import com.onkiup.linker.parser.annotation.CaptureLimit;
import com.onkiup.linker.parser.annotation.CapturePattern;
import com.onkiup.linker.parser.annotation.IgnoreCharacters;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(PartialToken.class)
public class RuleTokenTest {

  public static class TestRule implements Rule {
    private String first;
    private String second;
  }

  @Before
  public void setup() {
    PowerMockito.mockStatic(PartialToken.class);
  }

  @Test
  public void testAdvance() throws Exception {
    PartialToken child = Mockito.mock(PartialToken.class);
    Mockito.when(child.isPopulated()).thenReturn(true);
    PowerMockito.when(PartialToken.forField(Mockito.any(), Mockito.any(), Mockito.anyInt())).thenReturn(child);
    RuleToken token = new RuleToken(null, TestRule.class, 0);

    assertSame(child, token.advance(false).get());
    assertSame(child, token.advance(false).get());
    assertFalse(token.advance(false).isPresent());
    PowerMockito.verifyStatic(PartialToken.class, Mockito.times(2));
    PartialToken.forField(Mockito.eq(token), Mockito.any(Field.class), Mockito.anyInt());
  }

  @Test
  public void testRollback() throws Exception {
   PartialToken child = Mockito.mock(PartialToken.class);
   Mockito.when(child.isPopulated()).thenReturn(true);
   Mockito.when(child.pullback()).thenReturn(Optional.of(new StringBuilder("a")));
   PowerMockito.when(PartialToken.forField(Mockito.any(), Mockito.any(), Mockito.anyInt())).thenReturn(child);
   PartialToken parent = Mockito.mock(PartialToken.class);
   Mockito.when(parent.pushback(false)).thenReturn(Optional.of(new StringBuilder("aa")));

   RuleToken token = new RuleToken(parent, TestRule.class, 0);
   token.advance(false);
   token.advance(false);

   Optional<StringBuilder> rollbacked = token.pushback(false);
   StringBuilder result = rollbacked.get();
   assertEquals("aa", result.toString());
  }
}
