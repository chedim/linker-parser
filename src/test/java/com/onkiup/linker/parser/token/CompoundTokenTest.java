package com.onkiup.linker.parser.token;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.Test;
import org.mockito.Mockito;

import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.annotation.AdjustPriority;

public class CompoundTokenTest {

  public static interface CttJunction extends Rule {

  }

  @AdjustPriority(100)
  public static class CttConcrete implements CttJunction {

  }

  @Test
  public void forClass() {
    assertTrue(CompoundToken.forClass(CttJunction.class, null) instanceof VariantToken);
    assertTrue(CompoundToken.forClass(CttConcrete.class, null) instanceof RuleToken);
  }

  @Test
  public void traceback() {
    CompoundToken token = Mockito.mock(CompoundToken.class);
    CompoundToken compoundChild = Mockito.mock(CompoundToken.class);
    PartialToken sourceChild = Mockito.mock(PartialToken.class);

    Mockito.when(token.traceback()).thenCallRealMethod();
    Mockito.when(token.children()).thenReturn(new PartialToken[] {compoundChild, sourceChild});
    Mockito.when(compoundChild.traceback()).thenReturn(Optional.of("/COMPOUND_CHILD/"));
    Mockito.when(sourceChild.source()).thenReturn("/SOURCE_CHILD/");
    Mockito.when(compoundChild.alternativesLeft()).thenReturn(1);

    assertEquals("/SOURCE_CHILD//COMPOUND_CHILD/", token.traceback().get().toString());
    Mockito.verify(token, Mockito.times(0)).onFail();
    Mockito.verify(compoundChild, Mockito.times(1)).invalidate();
    Mockito.verify(compoundChild, Mockito.times(0)).onFail();
    Mockito.verify(sourceChild, Mockito.times(1)).invalidate();
    Mockito.verify(sourceChild, Mockito.times(1)).onFail();

    Mockito.when(token.children()).thenReturn(new PartialToken[] {sourceChild});
    assertEquals("/SOURCE_CHILD/", token.traceback().get().toString());
    Mockito.verify(token, Mockito.times(1)).onFail();
    Mockito.verify(token, Mockito.times(1)).invalidate();
    Mockito.verify(sourceChild, Mockito.times(2)).invalidate();
    Mockito.verify(sourceChild, Mockito.times(2)).onFail();
  }

  @Test
  public void alternativesLeft() {
    CompoundToken token = Mockito.mock(CompoundToken.class);
    PartialToken child1 = Mockito.mock(PartialToken.class);
    PartialToken child2 = Mockito.mock(PartialToken.class);

    Mockito.when(token.alternativesLeft()).thenCallRealMethod();
    Mockito.when(token.children()).thenReturn(new PartialToken[]{child1, child2});
    Mockito.when(child1.alternativesLeft()).thenReturn(3);
    Mockito.when(child2.alternativesLeft()).thenReturn(5);

    assertEquals(8, token.alternativesLeft());
  }

  @Test
  public void basePriority() {
    CompoundToken token = Mockito.mock(CompoundToken.class);
    PartialToken child = Mockito.mock(PartialToken.class);

    Mockito.when(token.basePriority()).thenCallRealMethod();
    Mockito.when(token.tokenType()).thenReturn(CttConcrete.class);
    Mockito.when(token.children()).thenReturn(new PartialToken[]{child});
    Mockito.when(child.basePriority()).thenReturn(900);
    Mockito.when(child.propagatePriority()).thenReturn(true);

    assertEquals(1000, token.basePriority());
  }

  @Test
  public void source() {
    CompoundToken token = Mockito.mock(CompoundToken.class);
    CompoundToken compoundChild = Mockito.mock(CompoundToken.class);
    PartialToken sourceChild = Mockito.mock(PartialToken.class);

    Mockito.when(token.traceback()).thenCallRealMethod();
    Mockito.when(token.children()).thenReturn(new PartialToken[] {compoundChild, sourceChild});
    Mockito.when(compoundChild.traceback()).thenReturn(Optional.of("/COMPOUND_CHILD/"));
    Mockito.when(sourceChild.source()).thenReturn("/SOURCE_CHILD/");

    assertEquals("/SOURCE_CHILD//COMPOUND_CHILD/", token.traceback().get().toString());
  }

  @Test
  public void visit() {
    CompoundToken token = Mockito.mock(CompoundToken.class);
    PartialToken child1 = Mockito.mock(PartialToken.class);
    PartialToken child2 = Mockito.mock(PartialToken.class);
    final LinkedList<PartialToken<?>> visited = new LinkedList<>();
    Consumer<PartialToken> visitor = visited::add;

    Mockito.doCallRealMethod().when(token).visit(visitor);
    Mockito.when(token.children()).thenReturn(new PartialToken[]{child1, child2});

    token.visit(visitor);

    assertEquals(1, visited.size());
    assertEquals(token, visited.get(0));
    Mockito.verify(child1, Mockito.times(1)).visit(visitor);
    Mockito.verify(child2, Mockito.times(1)).visit(visitor);
  }
}
