package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.Optional;

import org.junit.Test;

import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TokenGrammar;
import com.onkiup.linker.parser.annotation.AdjustPriority;
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


  @AdjustPriority(value=10000, propagate=true)
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

    UnrotationTestToken result = grammar.parse("1 + 2 * 3 + 4");
    assertNotNull(result);
    assertTrue(result instanceof UnrotationTestBinaryOperator);
    UnrotationTestBinaryOperator operator = (UnrotationTestBinaryOperator) result;

    UnrotationTestToken token = operator.right;
    assertNotNull(token);
    assertEquals(UnrotationTestNumber.class, token.getClass());
    UnrotationTestNumber number = (UnrotationTestNumber)token;
    assertEquals("4", number.value);

    assertTrue(operator.operator instanceof UnrotationTestPlusOperator);
    
    token = operator.left;
    assertTrue(token instanceof UnrotationTestBinaryOperator);
    operator = (UnrotationTestBinaryOperator)token;
    assertEquals(UnrotationTestPlusOperator.class, operator.operator.getClass());
    
    token = operator.left;
    assertTrue(token instanceof UnrotationTestNumber);
    number = (UnrotationTestNumber)token;
    assertEquals("1", number.value);

    token = operator.right;
    assertTrue(token instanceof UnrotationTestBinaryOperator);
    operator = (UnrotationTestBinaryOperator) token;

    token = operator.left;
    assertEquals(UnrotationTestNumber.class, token.getClass());
    number = (UnrotationTestNumber)token;
    assertEquals("2", number.value);

    assertEquals(UnrotationTestStarOperator.class, operator.operator.getClass());

    token = operator.right;
    assertEquals(UnrotationTestNumber.class, token.getClass());
    number = (UnrotationTestNumber)token;
    assertEquals("3", number.value);
  }
}
