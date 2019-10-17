package com.onkiup.linker.parser.token;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertSame;
import static junit.framework.TestCase.assertTrue;

import java.util.function.Function;

import org.junit.Test;
import org.mockito.Mockito;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TokenTestResult;
import com.onkiup.linker.parser.annotation.CapturePattern;
import com.onkiup.linker.parser.annotation.IgnoreCase;

public class EnumTokenTest {

  @IgnoreCase
  public enum TestEnum implements Rule {
    ONE, TWO, THREE,
    @CapturePattern("hello") FOUR;
  }

  private TestEnum targetField;

  @Test
  public void baseCases() throws Exception {
    CompoundToken parent = Mockito.mock(CompoundToken.class);
    ConsumingToken.ConsumptionState.rootBuffer(parent, "");
    EnumToken<TestEnum> subject = new EnumToken<TestEnum>(parent, getClass().getDeclaredField("targetField"), TestEnum.class  ,
        ParserLocation.ZERO);

    Function<CharSequence,TokenTestResult> matcher = subject.tokenMatcher();

    assertTrue(matcher.apply("ON").isContinue());
    assertEquals("ONE", matcher.apply("ONE").getToken());

    subject.onConsumeSuccess("ONE");
    assertSame(TestEnum.ONE, subject.token().get());
    subject.reset();

    assertTrue(matcher.apply("T").isContinue());
    subject.reset();
    assertEquals("two", matcher.apply("two").getToken());
    subject.onConsumeSuccess("two");
    assertSame(TestEnum.TWO, subject.token().get());
    subject.reset();

    assertTrue(matcher.apply("HELLO").isMatch());
    subject.onConsumeSuccess("HELLO");
    assertEquals("HELLO", matcher.apply("HELLO").getToken());
    assertSame(TestEnum.FOUR, subject.token().get());
    subject.reset();
  }
}
