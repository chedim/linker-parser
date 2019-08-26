package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.onkiup.linker.parser.annotation.CaptureLimit;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(PartialToken.class)
public class CollectionTokenTest {

  private String[] field;
  @CaptureLimit(min=2, max=3)
  private String[] limit;

  @Before
  public void setup() {
    PowerMockito.mockStatic(PartialToken.class);
  }

  @Test
  public void testAdvance() throws Exception {
    PartialToken member = Mockito.mock(PartialToken.class);
    Mockito.when(member.isPopulated()).thenReturn(false);

    PowerMockito.when(PartialToken.forClass(Mockito.any(), Mockito.any(), Mockito.anyInt())).thenReturn(member);
    Field field = getClass().getDeclaredField("field");

    CollectionToken token = new CollectionToken(null, field, 0);

    assertSame(member, token.advance(false).orElse(null));
    assertFalse(token.advance(false).isPresent());
    assertTrue(token.isPopulated());
  }

  @Test
  public void testLimits() throws Exception {
    PartialToken member = Mockito.mock(PartialToken.class);
    Mockito.when(member.isPopulated()).thenReturn(true);

    PowerMockito.when(PartialToken.forClass(Mockito.any(), Mockito.any(), Mockito.anyInt())).thenReturn(member);
    Field field = getClass().getDeclaredField("limit");

    CollectionToken token = new CollectionToken(null, field, 0);

    assertSame(member, token.advance(false).orElse(null));
    assertFalse(token.isPopulated());
    assertSame(member, token.advance(false).orElse(null));
    Assert.assertFalse(token.isPopulated());
    assertSame(member, token.advance(false).orElse(null));
    assertTrue(token.isPopulated());
    assertFalse(token.advance(false).isPresent());
    assertTrue(token.isPopulated());
  }
}

