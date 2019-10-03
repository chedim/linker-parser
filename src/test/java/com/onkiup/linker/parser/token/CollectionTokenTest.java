package com.onkiup.linker.parser.token;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;

import org.junit.Test;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.annotation.CaptureLimit;
import com.onkiup.linker.parser.annotation.CapturePattern;

public class CollectionTokenTest {
  @CapturePattern(".*")
  private String[] stringField;
  private Field arrayField = CollectionTokenTest.class.getDeclaredField("stringField");
  @CapturePattern(".*")
  @CaptureLimit(min=1)
  private String[] minLimitArray;
  private Field minLimitField = CollectionTokenTest.class.getDeclaredField("minLimitArray");
  @CapturePattern(".*")
  @CaptureLimit(max=2)
  private String[] maxLimitArray;
  private Field maxLimitField = CollectionTokenTest.class.getDeclaredField("maxLimitArray");

  public CollectionTokenTest() throws NoSuchFieldException {
  }

  @Test
  public void onChildPopulated() {
    CollectionToken<?> token = new CollectionToken(null, arrayField, String[].class, null);
    try {
      token.onChildPopulated();
      fail();
    } catch (IllegalStateException ise) {
      // this is expected
    }
    token = new CollectionToken<>(null, arrayField, String[].class, null);
    PartialToken child = token.nextChild().get();
    token.onChildPopulated();
    PartialToken[] children = token.children();
    assertEquals(1, children.length);
    assertSame(child, children[0]);
  }

  @Test
  public void onChildFailed() {
    CollectionToken<?> token = new CollectionToken<>(null, arrayField, String[].class, null);
    try {
      token.onChildFailed();
      fail();
    } catch (IllegalStateException ise) {
      // this is expected
    }
    token = new CollectionToken<>(null, arrayField, String[].class, null);
    PartialToken<?> child = token.nextChild().get();
    token.onChildFailed();
    PartialToken[] children = token.children();
    assertEquals(0, children.length);
    assertFalse(token.isFailed());
    assertTrue(token.isPopulated());

    token = new CollectionToken<>(null, minLimitField, String[].class, null);
    child = token.nextChild().get();
    token.onChildFailed();
    assertTrue(token.isFailed());
    assertFalse(token.isPopulated());
  }

  @Test
  public void source() {
    CollectionToken<?> token = new CollectionToken<>(null, maxLimitField, String[].class, null);
    TerminalToken child = (TerminalToken)token.nextChild().get();
    ConsumingToken.ConsumptionState.inject(child, new ConsumingToken.ConsumptionState("token1", "token1|"));
    child.onConsumeSuccess("token1");
    child.onPopulated(new ParserLocation("", 6, 0, 6));
    token.onChildPopulated();
    child = (TerminalToken)token.nextChild().get();
    ConsumingToken.ConsumptionState.inject(child, new ConsumingToken.ConsumptionState("token2", "token2"));
    child.onConsumeSuccess("token2");
    child.onPopulated(new ParserLocation("", 12, 0, 12));
    token.onChildPopulated();
    assertEquals("token1|token2", token.source().toString());
  }

  @Test
  public void unfilledChildren() {
    CollectionToken<?> token = new CollectionToken<>(null, maxLimitField, String[].class, null);
    assertEquals(2, token.unfilledChildren());
    token.nextChild();
    assertEquals(2, token.unfilledChildren());
    token.onChildPopulated();
    assertEquals(1, token.unfilledChildren());
    token.nextChild();
    assertEquals(1, token.unfilledChildren());
    token.onChildPopulated();
    assertEquals(0, token.unfilledChildren());
    assertTrue(token.isPopulated());
  }

  @Test
  public void alternativesLeft() {
    // TODO
  }
}
