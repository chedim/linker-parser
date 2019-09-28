package com.onkiup.linker.parser.token;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.Field;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onkiup.linker.parser.ParserLocation;

@PrepareForTest({PartialToken.class, LoggerFactory.class})
@RunWith(PowerMockRunner.class)
public class AbstractTokenTest {

  @Test
  public void testReadFlags() {
    PowerMockito.mockStatic(PartialToken.class);
    PowerMockito.when(PartialToken.getOptionalCondition(Mockito.any())).thenReturn(Optional.of("lalalalala"));
    PowerMockito.when(PartialToken.hasOptionalAnnotation(Mockito.any())).thenReturn(true);

    AbstractToken token = Mockito.mock(AbstractToken.class);
    Mockito.when(token.isOptional()).thenCallRealMethod();
    Mockito.when(token.optionalCondition()).thenCallRealMethod();
    Mockito.doCallRealMethod().when(token).readFlags(Mockito.any());

    token.readFlags(null);

    assertFalse(token.isOptional());
    assertEquals("lalalalala", token.optionalCondition().get());

    Mockito.when(PartialToken.getOptionalCondition(Mockito.any())).thenReturn(Optional.empty());
    token.readFlags(null);
    assertTrue(token.isOptional());
    assertFalse(token.optionalCondition().isPresent());

    Mockito.when(PartialToken.hasOptionalAnnotation(Mockito.any())).thenReturn(false);
    token.readFlags(null);
    assertFalse(token.isOptional());
    assertFalse(token.optionalCondition().isPresent());
  }

  @Test
  public void testToString() throws Exception {
    Field field = AbstractToken.class.getDeclaredField("field");
    AbstractToken token = Mockito.mock(AbstractToken.class);
    Mockito.when(token.toString()).thenCallRealMethod();

    // target field present
    Mockito.when(token.targetField()).thenReturn(Optional.of(field));
    assertEquals(AbstractToken.class.getName() + "$" + "field", token.toString());
    // target field not present
    Mockito.when(token.targetField()).thenReturn(Optional.empty());
    assertTrue(token.toString().startsWith(AbstractToken.class.getName() + "$MockitoMock$"));
  }

  @Test
  public void testOnPopulated() {
    ParserLocation end = Mockito.mock(ParserLocation.class);
    AbstractToken token = Mockito.mock(AbstractToken.class);
    Mockito.doCallRealMethod().when(token).onPopulated(Mockito.any());
    Mockito.when(token.end()).thenCallRealMethod();

    token.onPopulated(end);
    assertEquals(end, token.end());
  }

  @Test
  public void testLogging() {
    Logger logger = Mockito.mock(Logger.class);
    PowerMockito.mockStatic(LoggerFactory.class);
    Mockito.when(LoggerFactory.getLogger(Mockito.anyString())).thenReturn(logger);
    AbstractToken token = Mockito.mock(AbstractToken.class);
    Mockito.when(token.logger()).thenReturn(logger);
    Mockito.doCallRealMethod().when(token).log(Mockito.any(), Mockito.any());
    Mockito.doCallRealMethod().when(token).error(Mockito.any(), Mockito.any());

    Object[] vararg = new Object[0];
    token.log("", vararg);
    Mockito.verify(logger, Mockito.times(1)).debug("", vararg);
    token.error("", null);
    Mockito.verify(logger, Mockito.times(1)).error("", (Throwable)null);
  }
}

