package com.onkiup.linker.parser.token;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Test;

import com.onkiup.linker.parser.NumberMatcher;

public class NumberMatcherTest {

  @Test
  public void testBytes() {
    NumberMatcher subject = new NumberMatcher(Byte.class);
    assertTrue(subject.apply("1").isMatchContinue());
    assertTrue(subject.apply("128").isFailed());

    assertTrue(subject.apply("125 ").isMatch());
    assertEquals((byte)125, subject.apply("125i").getToken());
    assertEquals((byte)-125, subject.apply("-125i").getToken());
    assertEquals(3, subject.apply("125i").getTokenLength());
  }

  @Test
  public void testIntegers() {
    NumberMatcher subject = new NumberMatcher(Integer.class);
    assertTrue(subject.apply("1").isMatchContinue());
    assertTrue(subject.apply("999999999999999999999999999").isFailed());

    assertTrue(subject.apply("199 ").isMatch());
    assertEquals(199, subject.apply("199i").getToken());
    assertEquals(-199, subject.apply("-199i").getToken());
    assertEquals(3, subject.apply("199i").getTokenLength());
  }

  @Test
  public void testLongs() {
    NumberMatcher subject = new NumberMatcher(Long.class);
    assertTrue(subject.apply("1").isMatchContinue());
    assertTrue(subject.apply("999999999999999999999999999").isFailed());

    assertTrue(subject.apply("199 ").isMatch());
    assertEquals(199L, subject.apply("199i").getToken());
    assertEquals(-199L, subject.apply("-199i").getToken());
    assertEquals(3, subject.apply("199i").getTokenLength());
  }

  @Test
  public void testFloats() {
    NumberMatcher subject = new NumberMatcher(Float.class);
    assertTrue(subject.apply("1").isMatchContinue());

    assertTrue(subject.apply("19.9 ").isMatch());
    assertEquals(Float.POSITIVE_INFINITY, subject.apply("99999999999999999999999999999999999999999999999999999999999999999999999999999").getToken());
    assertEquals(Float.NEGATIVE_INFINITY, subject.apply("-99999999999999999999999999999999999999999999999999999999999999999999999999999").getToken());
    assertEquals(19.9F, subject.apply("19.9i").getToken());
    assertEquals(-19.9F, subject.apply("-19.9i").getToken());
    assertEquals(4, subject.apply("19.9i").getTokenLength());
  }

  @Test
  public void testDoubles() {
    NumberMatcher subject = new NumberMatcher(Double.class);
    assertTrue(subject.apply("1").isMatchContinue());

    assertTrue(subject.apply("19.9 ").isMatch());
    assertEquals(Double.POSITIVE_INFINITY, subject.apply("999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999").getToken());
    assertEquals(Double.NEGATIVE_INFINITY, subject.apply("-999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999").getToken());
    assertEquals(19.9D, subject.apply("19.9i").getToken());
    assertEquals(-19.9D, subject.apply("-19.9i").getToken());
    assertEquals(4, subject.apply("19.9i").getTokenLength());
  }

  @Test
  public void testBigDecimals() {
    NumberMatcher subject = new NumberMatcher(BigDecimal.class);
    assertTrue(subject.apply("1").isMatchContinue());

    assertTrue(subject.apply("19.9 ").isMatch());
    assertEquals(new BigDecimal("19.9"), subject.apply("19.9i").getToken());
    assertEquals(new BigDecimal("-19.9"), subject.apply("-19.9i").getToken());
    assertEquals(4, subject.apply("19.9i").getTokenLength());
  }

  @Test
  public void testBigIntegers() {
    NumberMatcher subject = new NumberMatcher(BigDecimal.class);
    assertTrue(subject.apply("1").isMatchContinue());

    assertTrue(subject.apply("19.9 ").isMatch());
    assertEquals(new BigInteger("199"), subject.apply("199i").getToken());
    assertEquals(new BigInteger("-199"), subject.apply("-199i").getToken());
    assertEquals(3, subject.apply("199i").getTokenLength());
  }
}
