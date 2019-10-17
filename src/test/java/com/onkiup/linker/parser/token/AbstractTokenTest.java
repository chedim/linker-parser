package com.onkiup.linker.parser.token;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import com.onkiup.linker.parser.ParserLocation;

public class AbstractTokenTest {

  @Test
  public void markOptional() {
    AbstractToken abstractToken = Mockito.mock(AbstractToken.class);
    Mockito.doCallRealMethod().when(abstractToken).markOptional();
    Mockito.when(abstractToken.isOptional()).thenCallRealMethod();
    abstractToken.markOptional();
    assertTrue(abstractToken.isOptional());
  }

  @Test
  public void dropPopulated() {
    AbstractToken abstractToken = Mockito.mock(AbstractToken.class);
    ParserLocation location = Mockito.mock(ParserLocation.class);
    Mockito.doCallRealMethod().when(abstractToken).onPopulated(location);
    Mockito.when(abstractToken.isPopulated()).thenCallRealMethod();
    Mockito.doCallRealMethod().when(abstractToken).dropPopulated();

    abstractToken.onPopulated(location);
    assertTrue(abstractToken.isPopulated());
    abstractToken.dropPopulated();
    assertFalse(abstractToken.isPopulated());
  }

  @Test
  public void isFailed() {
    AbstractToken abstractToken = Mockito.mock(AbstractToken.class);
    Mockito.doCallRealMethod().when(abstractToken).onFail();
    Mockito.when(abstractToken.isFailed()).thenCallRealMethod();
    abstractToken.onFail();
    assertTrue(abstractToken.isFailed());
  }

  @Test
  public void location() {
  }

  @Test
  public void testLocation() {
  }

  @Test
  public void end() {
  }

  @Test
  public void parent() {
  }

  @Test
  public void targetField() {
  }

  @Test
  public void onPopulated() {
    ParserLocation location = Mockito.mock(ParserLocation.class);
    AbstractToken abstractToken = Mockito.mock(AbstractToken.class);
    Mockito.doCallRealMethod().when(abstractToken).onPopulated(location);
    Mockito.when(abstractToken.isPopulated()).thenCallRealMethod();
    abstractToken.onPopulated(location);
    assertTrue(abstractToken.isPopulated());
  }

  @Test
  public void logger() {
  }

  @Test
  public void tag() {
  }

  @Test
  public void testToString() {
  }

  @Test
  public void readFlags() {
  }

  @Test
  public void onFail() {
  }

  @Test
  public void optionalCondition() {
  }

  @Test
  public void addMetaToken() {
  }

  @Test
  public void metaTokens() {
  }
}
