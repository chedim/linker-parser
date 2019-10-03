package com.onkiup.linker.parser;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.onkiup.linker.parser.token.PartialToken;
import com.onkiup.linker.parser.token.RuleToken;
import com.onkiup.linker.parser.token.VariantToken;

public class SyntaxError extends RuntimeException {

  private PartialToken<?> expected;
  private CharSequence source;
  private String message;

  public SyntaxError(String message, PartialToken expected, CharSequence source) {
    this.message = message;
    this.expected = expected;
    this.source = source;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder("Parser error:")
      .append(message)
      .append("\n")
      .append("\tExpected ")
      .append(expected)
      .append(" but got: '")
      .append(expected != null && source != null && expected.position() < source.length() ? source.subSequence(expected.position(), source.length()) : source)
      .append("'\n\tSource:\n\t\t")
      .append(source)
      .append("\n\n\tTraceback:\n");

    if (expected != null) {
      expected.path().stream()
          .map(PartialToken::toString)
          .map(text -> text.replaceAll("\n", "\n\t\t") + '\n')
          .forEach(result::append);
    } else {
      result.append("No traceback provided");
    }

    return result.toString();
  }
}

