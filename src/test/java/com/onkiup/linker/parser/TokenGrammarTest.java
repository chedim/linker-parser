package com.onkiup.linker.parser;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class TokenGrammarTest {

  public static interface Junction extends Rule<Object> {
  
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
    private static final String tail = "*/";
  }

  public static class StatementSeparator implements Junction {
    @CapturePattern(pattern = "[\\s;]+")
    private String value;
  }

  public static class ArrayToken implements Rule<Object> {
    private Junction[] tokens = new Junction[3];
  }

  public static class Evaluatable implements Rule<Map<String, Object>> {
    private static final String VAR_MARKER = "$";
    @CapturePattern(pattern = "[^\\s\\n;]+")
    private String varName;

    @Override
    public void accept(Map<String, Object> context) {
      context.put(varName, context.get(varName) + "test");
    }
  }

  @Test
  public void testGrammar() throws Exception {
    TokenGrammar<?, TestGrammarDefinition> grammar = TokenGrammar.forClass(TestGrammarDefinition.class);
    Assert.assertNotNull(grammar);
    TestGrammarDefinition token = grammar.parse(new StringReader(":test"));
    Assert.assertEquals("test", token.command);
  }

  @Test
  public void testTrailingCharactersException() throws Exception {
    TokenGrammar<?, TestGrammarDefinition> grammar = TokenGrammar.forClass(TestGrammarDefinition.class);
    Assert.assertNotNull(grammar);
    try {
      TestGrammarDefinition result = grammar.parse(new StringReader(":test; :another;"));
      Assert.fail();
    } catch (Exception e) {
      // this is expected
    }
  }

  @Test
  public void testJunction() throws Exception {
    TokenGrammar<Object, Junction> grammar = TokenGrammar.forClass(Junction.class);
    Assert.assertNotNull(grammar);

    Junction result = grammar.parse(new StringReader("// comment"));
    Assert.assertTrue(result instanceof CommentGrammarDefinition);
    CommentGrammarDefinition comment = (CommentGrammarDefinition) result;
    Assert.assertEquals(" comment", comment.comment);
  }

  @Test
  public void testCapture() throws Exception {
    TokenGrammar<Object, Junction> grammar = TokenGrammar.forClass(Junction.class);
    Assert.assertNotNull(grammar);

    Junction result = grammar.parse(new StringReader("/* comment */"));
    Assert.assertTrue(result instanceof MultilineCommentGrammarDefinition);
  }

  @Test
  public void testEvaluation() throws Exception {
    TokenGrammar<Map<String, Object>, Evaluatable> grammar = TokenGrammar.forClass(Evaluatable.class);

    Map<String, Object> context = new HashMap<>();
    context.put("test", 100);

    grammar.parse(new StringReader("$test"), context);
    Assert.assertEquals("100test", context.get("test"));
  }

  @Test
  public void testArrayCapture() throws Exception {
    TokenGrammar<Object, ArrayToken> grammar = TokenGrammar.forClass(ArrayToken.class);
    String test = ":hello; // comment\n/* multiline\ncomment */";

    ArrayToken result = grammar.parse(new StringReader(test));
    Assert.assertNotNull(result);
    Assert.assertNotNull(result.tokens);

    Junction[] tokens = result.tokens;
    Assert.assertEquals(3, tokens.length);
    
    Junction token = tokens[0];
    Assert.assertTrue(token instanceof TestGrammarDefinition);
    Assert.assertEquals("hello", ((TestGrammarDefinition) token).command);

    Assert.assertTrue(false);
  }
}
