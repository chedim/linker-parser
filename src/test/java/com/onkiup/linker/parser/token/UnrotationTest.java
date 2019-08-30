package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.Optional;

import org.junit.Test;

import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TokenGrammar;
import com.onkiup.linker.parser.annotation.CaptureLimit;
import com.onkiup.linker.parser.annotation.CapturePattern;
import com.onkiup.linker.parser.annotation.IgnoreCharacters;

import static org.junit.Assert.*;

public class UnrotationTest {

  public static class TestRule implements Rule {
    private String first;
    private String second;
  }


  public static interface UnrotationTestToken extends Rule {
  
  }

  public static interface UnrotationTestOperator extends Rule {
  
  }

  public static class UnrotationTestNumber implements UnrotationTestToken {

    @CapturePattern("\\d+")
    private String value;
  
  }

  public static class UnrotationTestPlusOperator implements UnrotationTestOperator {
    public static final String MARKER = "+";
  }

  public static class UnrotationTestStarOperator implements UnrotationTestOperator {
    public static final String MARKER = "*";
  }

  @IgnoreCharacters(" ")
  public static class UnrotationTestBinaryOperator implements UnrotationTestToken {
    private UnrotationTestToken left;
    private UnrotationTestOperator operator;
    private UnrotationTestToken right;
  }

  @Test
  public void testUnrotation() throws Exception {
    TokenGrammar<UnrotationTestToken> grammar = TokenGrammar.forClass(UnrotationTestToken.class);

    UnrotationTestToken result = grammar.parse("1 + 2 * 3");
    assertNotNull(result);
    assertTrue(result instanceof UnrotationTestBinaryOperator);
    UnrotationTestBinaryOperator operator = (UnrotationTestBinaryOperator) result;

    UnrotationTestToken token = operator.left;
    assertNotNull(token);
    assertTrue(token instanceof UnrotationTestNumber);
    UnrotationTestNumber number = (UnrotationTestNumber)token;
    assertEquals("1", number.value);

    assertTrue(operator.operator instanceof UnrotationTestPlusOperator);
    
    token = operator.right;
    assertTrue(token instanceof UnrotationTestBinaryOperator);
    operator = (UnrotationTestBinaryOperator)token;
    
    token = operator.left;
    assertTrue(token instanceof UnrotationTestNumber);
    number = (UnrotationTestNumber)token;
    assertEquals("2", number.value);

    assertTrue(operator.operator instanceof UnrotationTestStarOperator);

    token = operator.right;
    assertTrue(token instanceof UnrotationTestNumber);
    number = (UnrotationTestNumber)token;
    assertEquals("3", number.value);
  }
}
