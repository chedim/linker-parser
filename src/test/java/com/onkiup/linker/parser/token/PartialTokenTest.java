package com.onkiup.linker.parser.token;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertSame;
import static junit.framework.TestCase.assertTrue;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.annotation.AdjustPriority;
import com.onkiup.linker.parser.annotation.OptionalToken;
import com.onkiup.linker.parser.annotation.SkipIfFollowedBy;

@RunWith(PowerMockRunner.class)
@PrepareForTest(PartialToken.class)
public class PartialTokenTest {

  private Object[] array;

  public PartialTokenTest() throws NoSuchFieldException {
  }

  @AdjustPriority(value = 9000, propagate = true)
  private static class TestRule implements Rule {}
  @SkipIfFollowedBy("test")
  private TestRule testRule;
  @OptionalToken(whenFollowedBy = "hi")
  private Rule rule;
  @OptionalToken
  private String string;
  private enum TestEnum {};
  private TestEnum enumnumnum;

  private Field arrayField = PartialTokenTest.class.getDeclaredField("array");
  private Field testRuleField = PartialTokenTest.class.getDeclaredField("testRule");
  private Field junctionField = PartialTokenTest.class.getDeclaredField("rule");
  private Field stringField = PartialTokenTest.class.getDeclaredField("string");
  private Field enumField = PartialTokenTest.class.getDeclaredField("enumnumnum");

  @Test
  public void forField() throws Exception {

    Class[] constructorArguments = new Class[] {
        CompoundToken.class, Field.class, Class.class, ParserLocation.class
    };

    PowerMockito.whenNew(CollectionToken.class).withAnyArguments().thenReturn(Mockito.mock(CollectionToken.class));
    PowerMockito.whenNew(VariantToken.class).withAnyArguments().thenReturn(Mockito.mock(VariantToken.class));
    PowerMockito.whenNew(RuleToken.class).withAnyArguments().thenReturn(Mockito.mock(RuleToken.class));
    PowerMockito.whenNew(TerminalToken.class).withAnyArguments().thenReturn(Mockito.mock(TerminalToken.class));
    PowerMockito.whenNew(EnumToken.class).withAnyArguments().thenReturn(Mockito.mock(EnumToken.class));

    assertTrue(PartialToken.forField(null, arrayField, Object[].class, null) instanceof CollectionToken);
    assertTrue(PartialToken.forField(null, testRuleField, TestRule.class, null) instanceof RuleToken);
    assertTrue(PartialToken.forField(null, junctionField, Rule.class, null) instanceof VariantToken);
    assertTrue(TerminalToken.class.isAssignableFrom(PartialToken.forField(null, stringField, String.class, null).getClass()));
    assertTrue(PartialToken.forField(null, enumField, TestEnum.class, null) instanceof EnumToken);

  }

  @Test
  public void getOptionalCondition() {
    assertFalse(PartialToken.getOptionalCondition(enumField).isPresent());
    assertFalse(PartialToken.getOptionalCondition(stringField).isPresent());
    assertEquals("test", PartialToken.getOptionalCondition(testRuleField).get());
    assertEquals("hi", PartialToken.getOptionalCondition(junctionField).get());
  }

  @Test
  public void isOptional() {
    assertTrue(PartialToken.hasOptionalAnnotation(testRuleField));
    assertTrue(PartialToken.hasOptionalAnnotation(junctionField));
    assertTrue(PartialToken.hasOptionalAnnotation(stringField));
    assertFalse(PartialToken.hasOptionalAnnotation(enumField));
  }

  @Test
  public void lookahead() {
    PartialToken token = Mockito.mock(PartialToken.class);
    CompoundToken parent = Mockito.mock(CompoundToken.class);

    Mockito.when(token.parent()).thenReturn(Optional.of(parent));
    Mockito.when(parent.parent()).thenReturn(Optional.empty());
    Mockito.when(token.lookahead(Mockito.any())).thenCallRealMethod();
    Mockito.when(parent.lookahead(Mockito.any())).thenCallRealMethod();
    Mockito.when(token.targetField()).thenReturn(Optional.of(testRuleField));
    Mockito.when(parent.targetField()).thenReturn(Optional.of(junctionField));

    assertFalse(token.lookahead("m"));
    assertTrue(token.lookahead("t"));
    assertTrue(token.lookahead("h"));
    assertTrue(token.lookahead("test"));
    Mockito.verify(parent, Mockito.times(0)).markOptional();
    Mockito.verify(token, Mockito.times(1)).markOptional();
    assertFalse(token.lookahead("hi"));
    Mockito.verify(parent, Mockito.times(1)).markOptional();
    assertFalse(token.lookahead("testt"));
    Mockito.verify(token, Mockito.times(2)).markOptional();
    assertTrue(token.lookahead("testh"));
    Mockito.verify(token, Mockito.times(3)).markOptional();
    Mockito.verify(parent, Mockito.times(1)).markOptional();
    assertFalse(token.lookahead("testhi"));
    Mockito.verify(token, Mockito.times(4)).markOptional();
    Mockito.verify(parent, Mockito.times(2)).markOptional();
  }

  @Test
  public void findInTree() {
    PartialToken token = Mockito.mock(PartialToken.class);
    CompoundToken parent = Mockito.mock(CompoundToken.class);

    Mockito.when(token.parent()).thenReturn(Optional.of(parent));
    Mockito.when(parent.parent()).thenReturn(Optional.empty());
    Mockito.when(token.findInPath(Mockito.any())).thenCallRealMethod();
    Mockito.when(parent.findInPath(Mockito.any())).thenCallRealMethod();

    assertEquals(parent, token.findInPath(parent::equals).get());
  }

  @Test
  public void position() {
    PartialToken token = Mockito.mock(PartialToken.class);
    Mockito.when(token.position()).thenCallRealMethod();

    assertEquals(0, token.position());
    ParserLocation location = Mockito.mock(ParserLocation.class);
    Mockito.when(location.position()).thenReturn(9000);
    Mockito.when(token.location()).thenReturn(location);
    assertEquals(9000, token.position());
  }

  @Test
  public void basePriority() {
    PartialToken token = Mockito.mock(PartialToken.class);
    Mockito.when(token.tokenType()).thenReturn(TestRule.class);
    Mockito.when(token.basePriority()).thenCallRealMethod();
    assertEquals(9000, token.basePriority());
  }

  @Test
  public void propagatePriority() {
    PartialToken token = Mockito.mock(PartialToken.class);
    Mockito.when(token.tokenType()).thenReturn(TestRule.class);
    Mockito.when(token.propagatePriority()).thenCallRealMethod();
    assertTrue(token.propagatePriority());
  }

  @Test
  public void visit() {
    PartialToken<?> token = Mockito.mock(PartialToken.class);
    Mockito.doCallRealMethod().when(token).visit(Mockito.any());
    PartialToken[] visited = new PartialToken[1];
    token.visit(t -> visited[0] = t);
    assertEquals(token, visited[0]);
  }

  @Test
  public void alternativesLeft() {
    PartialToken token = Mockito.mock(PartialToken.class);
    Mockito.when(token.alternativesLeft()).thenCallRealMethod();
    assertEquals(0, token.alternativesLeft());
  }

  @Test
  public void root() {
    PartialToken parent = Mockito.mock(PartialToken.class);
    PartialToken child = Mockito.mock(PartialToken.class);

    Mockito.when(child.parent()).thenReturn(Optional.of(parent));
    Mockito.when(parent.parent()).thenReturn(Optional.empty());
    Mockito.when(child.root()).thenCallRealMethod();
    Mockito.when(parent.root()).thenCallRealMethod();

    assertEquals(parent, child.root());
  }

  @Test
  public void tail() {
    PartialToken token = Mockito.mock(PartialToken.class);
    Mockito.when(token.tail(Mockito.anyInt())).thenCallRealMethod();
    Mockito.when(token.source()).thenReturn("source");

    assertEquals("source", token.tail(6));
    assertEquals(" source", token.tail(7));
    assertEquals("rce", token.tail(3));
  }

  @Test
  public void path() {
    PartialToken token = Mockito.mock(PartialToken.class);
    CompoundToken parent = Mockito.mock(CompoundToken.class);

    Mockito.when(token.parent()).thenReturn(Optional.of(parent));
    Mockito.when(parent.parent()).thenReturn(Optional.empty());
    Mockito.when(token.path()).thenCallRealMethod();
    Mockito.when(parent.path()).thenCallRealMethod();

    LinkedList<PartialToken> path = token.path();
    assertEquals(2, path.size());
    assertSame(parent, path.get(0));
    assertSame(token, path.get(1));
  }
}
