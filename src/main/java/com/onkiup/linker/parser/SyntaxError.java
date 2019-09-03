package com.onkiup.linker.parser;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.onkiup.linker.parser.token.PartialToken;
import com.onkiup.linker.parser.token.RuleToken;
import com.onkiup.linker.parser.token.VariantToken;

public class SyntaxError extends RuntimeException {

  private PartialToken lastToken;
  private StringBuilder source;
  private String message;

  public SyntaxError(String message, PartialToken lastToken, StringBuilder source) {
    this.message = message;
    this.lastToken = lastToken;
    this.source = source;
  }

  @Override
  public String toString() {
    PartialToken expected = lastToken.expected();
    StringBuilder result = new StringBuilder("Parser error:")
      .append(message)
      .append("\n")
      .append("\tExpected ")
      .append(expected)
      .append(" but got: '")
      .append(expected != null && source != null && expected.position() < source.length() ? source.substring(expected.position()) : source)
      .append("'\n\tSource:\n\t\t")
      .append(source)
      .append("\n\n\tTraceback:\n\t\t");

    PartialToken parent = expected;
    while (null != (parent = (PartialToken) parent.getParent().orElse(null))) {
      result.append(parent.toString().replace("\n", "\n\t\t"));
    }

    return result.toString();
  }
}

