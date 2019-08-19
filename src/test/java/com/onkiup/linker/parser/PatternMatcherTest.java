package com.onkiup.linker.parser;
import org.mockito.Mockito;
import org.junit.Assert;
import org.junit.Test;

public class PatternMatcherTest {

  @Test
  public void testMatch() {
    CapturePattern pattern = Mockito.mock(CapturePattern.class);
    Mockito.when(pattern.value()).thenReturn("");
    Mockito.when(pattern.pattern()).thenReturn("[^;\\s]+");
    Mockito.when(pattern.replacement()).thenReturn("");
    Mockito.when(pattern.until()).thenReturn("");
    PatternMatcher subject = new PatternMatcher(pattern);

    StringBuilder buffer = new StringBuilder();
    TokenTestResult result;
    
    buffer.append("a");
    result = subject.apply(buffer);
    Assert.assertEquals(TestResult.MATCH_CONTINUE, result.getResult());
    Assert.assertEquals("a", result.getToken());

    buffer.append(";");
    result = subject.apply(buffer);
    Assert.assertEquals(TestResult.MATCH, result.getResult());
    Assert.assertEquals("a", result.getToken());

    buffer = new StringBuilder("; test");
    result = subject.apply(buffer);

    Assert.assertEquals(TestResult.FAIL, result.getResult());
  }

  @Test
  public void testReplace() {
    CapturePattern pattern = Mockito.mock(CapturePattern.class);
    Mockito.when(pattern.pattern()).thenReturn("([^;\\s]+)");
    Mockito.when(pattern.replacement()).thenReturn("$1--");
    Mockito.when(pattern.until()).thenReturn("");

    PatternMatcher subject = new PatternMatcher(pattern);
    StringBuilder buffer = new StringBuilder("test; data");

    TokenTestResult result = subject.apply(buffer);

    Assert.assertTrue(result.isMatch());
    Assert.assertEquals(4, result.getTokenLength());
    Assert.assertEquals("test--", result.getToken());
  }
  
  @Test
  public void testUntil() {
    CapturePattern pattern = Mockito.mock(CapturePattern.class);
    Mockito.when(pattern.pattern()).thenReturn("");
    Mockito.when(pattern.value()).thenReturn("");
    Mockito.when(pattern.replacement()).thenReturn(">($1)<");
    Mockito.when(pattern.until()).thenReturn("(until)");

    PatternMatcher subject = new PatternMatcher(pattern);
    StringBuilder buffer = new StringBuilder("s");

    TokenTestResult result = subject.apply(buffer);
    Assert.assertTrue(result.isMatchContinue());
    Assert.assertEquals(1, result.getTokenLength());
    Assert.assertEquals("s", result.getToken());

    buffer.append("ome text unti");
    result = subject.apply(buffer);
    Assert.assertTrue(result.isMatchContinue());
    Assert.assertEquals(buffer.length(), result.getTokenLength());
    Assert.assertEquals("some text unti", result.getToken());

    buffer.append("l");
    result = subject.apply(buffer);
    Assert.assertTrue(result.isMatch());
    Assert.assertEquals("some text >(until)<", result.getToken());
    Assert.assertEquals(15, result.getTokenLength());

    // NO replacement
    Mockito.when(pattern.replacement()).thenReturn("");
    Mockito.when(pattern.until()).thenReturn("until");
    subject = new PatternMatcher(pattern);
    result = subject.apply(buffer);
    Assert.assertTrue(result.isMatch());
    Assert.assertEquals("some text ", result.getToken());
    Assert.assertEquals(10, result.getTokenLength());

    result = subject.apply(new StringBuilder("until"));
    Assert.assertEquals(TestResult.FAIL, result.getResult());
  }
}
