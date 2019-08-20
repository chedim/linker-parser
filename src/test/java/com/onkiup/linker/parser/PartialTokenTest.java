package com.onkiup.linker.parser;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.Test;

import static org.junit.Assert.*;

public class PartialTokenTest {

  public static class TestGrammarWithTransientFields implements Rule {
    private transient String firstTransient;
    private String firstNonTransient;
    private transient String transientField;
    private String secondNonTransient;
    private transient String lastTransient;
  }

  @IgnoreCharacters(" \t\n")
  public static class TestGrammarIgnoreCharacters implements Rule {
    private Integer number;
  }

  @Test
  public void testSkippingTransientFields() {
    PartialToken token = new PartialToken(null, TestGrammarWithTransientFields.class, null);

    assertFalse(token.isPopulated());
    int fieldCount, expectedFields = 2;

    for (fieldCount = 0; fieldCount < expectedFields; fieldCount++) {
      Field currentField = token.getCurrentField();
      assertNotNull(currentField);
      assertFalse("Got a transient field '" + currentField.getName() + "'", Modifier.isTransient(currentField.getModifiers()));

      token.populateField("test");
    }

    assertTrue(token.isPopulated());
  }

  @Test
  public void testReturnsTaken() {
    PartialToken token = new PartialToken(null, TestGrammarWithTransientFields.class, null);
    
    token.populateField("test");
    token.appendTaken("test");
    token.populateField(null);

    StringBuilder result = token.getTaken();

    assertEquals("test", result.toString());
  }

  @Test
  public void testIgnoringCharacters() throws Exception {
    PartialToken token = new PartialToken(null, TestGrammarIgnoreCharacters.class, null);
    token.setMatcher(new NumberMatcher(Integer.class));

    TokenTestResult result = token.test(new StringBuilder("  \n\t123"));
    assertNotNull(result);
    assertEquals("123", result.getToken());
  }

  @Test
  public void testMetadata() throws Exception {
    PartialToken<TestGrammarIgnoreCharacters> parentToken = new PartialToken(null, TestGrammarIgnoreCharacters.class, null);
    PartialToken<TestGrammarIgnoreCharacters> childToken = new PartialToken(parentToken, TestGrammarIgnoreCharacters.class, null);

    TestGrammarIgnoreCharacters parent = parentToken.getToken();
    TestGrammarIgnoreCharacters child = childToken.getToken();
    assertNotNull(child);
    assertEquals(parent, child.parent());
  }
}

