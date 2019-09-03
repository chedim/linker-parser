package com.onkiup.linker.parser.token;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(PartialToken.class)
public class VariantTokenTest {

  public interface Variant extends Rule {

  }

  public static class VariantA implements Variant {
  
  }

  public static class VariantB implements Variant {
  
  }

  @Before
  public void setup() {
    PowerMockito.mockStatic(PartialToken.class);
  }

  @Test
  public void testAdvance() {
    PartialToken target = Mockito.mock(PartialToken.class);
    PartialToken child = Mockito.mock(PartialToken.class);
    Mockito.when(child.isPopulated()).thenReturn(false);
    Mockito.when(child.advance(Mockito.anyBoolean())).thenReturn(Optional.of(target));
    Mockito.when(child.source()).thenReturn(new StringBuilder("123"));
    PowerMockito.when(PartialToken.forClass(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(child);

    VariantToken token = new VariantToken(null, Variant.class, ParserLocation.ZERO);

    assertSame(target, token.advance(false).get());
    assertSame(target, token.advance(false).get());
    Mockito.when(child.isPopulated()).thenReturn(true);
    assertTrue(token.isPopulated());

    PowerMockito.verifyStatic(PartialToken.class, Mockito.times(1));
    PartialToken.forClass(Mockito.eq(token), Mockito.eq(VariantA.class), Mockito.any());
    PartialToken.forClass(Mockito.eq(token), Mockito.eq(VariantB.class), Mockito.any());

    assertEquals("123", token.source().toString());
  }

}
