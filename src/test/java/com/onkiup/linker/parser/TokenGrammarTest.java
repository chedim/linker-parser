package com.onkiup.linker.parser;

import java.io.StringReader;

import org.junit.Test;
import org.junit.Assert;

public class TokenGrammarTest {

  public static interface Junction extends Rule {
  
  }

  public static class TestGrammarDefinition implements Junction {
    private static final String marker = ":";
    @CapturePattern(pattern = "[^;\\n]+")
    private String command;
  }

  public static class CommentGrammarDefinition implements Junction {
    private static final String marker = "//";
    @CapturePattern(pattern = "[^\\n]*")
    private String comment;
  }

  public static class MultilineCommentGrammarDefinition implements Junction {
    private static final String marker = "/*";
    @CapturePattern(until="\\*/")
    private String comment;

  }

  @Test
  public void testGrammar() throws Exception {
    TokenGrammar<TestGrammarDefinition> grammar = TokenGrammar.forClass(TestGrammarDefinition.class);
    Assert.assertNotNull(grammar);
    TestGrammarDefinition token = grammar.parse(new StringReader(":test"), false);
    Assert.assertEquals("test", token.command);
  }

  @Test
  public void testTrailingCharactersException() throws Exception {
    TokenGrammar<TestGrammarDefinition> grammar = TokenGrammar.forClass(TestGrammarDefinition.class);
    Assert.assertNotNull(grammar);
    try {
      TestGrammarDefinition result = grammar.parse(new StringReader(":test; :another;"), false);
      Assert.fail();
    } catch (Exception e) {
      // this is expected
    }
  }

  @Test
  public void testJunction() throws Exception {
    TokenGrammar<Junction> grammar = TokenGrammar.forClass(Junction.class);
    Assert.assertNotNull(grammar);

    Junction result = grammar.parse(new StringReader("// comment"));
    Assert.assertTrue(result instanceof CommentGrammarDefinition);
    CommentGrammarDefinition comment = (CommentGrammarDefinition) result;
    Assert.assertEquals(" comment", comment.comment);
  }

  @Test
  public void testCapture() throws Exception {
    TokenGrammar<Junction> grammar = TokenGrammar.forClass(Junction.class);
    Assert.assertNotNull(grammar);

    Junction result = grammar.parse(new StringReader("/* comment */"));
    Assert.assertTrue(result instanceof MultilineCommentGrammarDefinition);
  }

  @Test
  public void testEvaluation() throws Exception {
    TokenGrammar<Evaluatable> grammr = TokenGrammar.forClass(Evaluatable.class);

    
  }
}
