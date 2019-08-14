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

  @Test
  public void testSkippingTransientFields() {
    PartialToken token = new PartialToken(TestGrammarWithTransientFields.class, null);

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
}

