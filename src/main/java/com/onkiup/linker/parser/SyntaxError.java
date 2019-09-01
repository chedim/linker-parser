package com.onkiup.linker.parser;

import java.lang.reflect.Field;
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
  public StackTraceElement[] getStackTrace() {
    List<StackTraceElement> result = new LinkedList<>();
    PartialToken parent = lastToken;

    do {

      Class tokenType = parent.getTokenType();
      String type = null;
      String method = null;
      String filename = null;
      int position = parent.position() + parent.consumed();
      filename = source.substring(parent.position(), parent.consumed());
      filename = String.format("%10s", filename.length() < 10 ? filename : filename.substring(filename.length() - 10));

      if (parent instanceof VariantToken) {
        VariantToken variant = (VariantToken) parent;
        type = tokenType.getSimpleName();
        PartialToken token = variant.resolvedAs();
        if (token != null) {
          method = token.getTokenType().getSimpleName();
        } else {
          method = "???";
        }
      } else if (parent instanceof RuleToken) {
        Field field = ((RuleToken)parent).getCurrentField();
        type = tokenType.getSimpleName();
        method = field.getName();
      }

      if (type != null) {
        StackTraceElement element = new StackTraceElement(
          type, method, filename, position
        );
        result.add(element);
      }

      parent = (PartialToken) parent.getParent().orElse(null);

    } while (parent != null);

    StackTraceElement[] elements = new StackTraceElement[result.size()];

    return result.toArray(elements);
  }

  @Override
  public String toString() {
    StackTraceElement[] elements = getStackTrace();
    StringBuilder result = new StringBuilder();
    result.append(message).append("\n");
    for (StackTraceElement element : elements) {
      result.append("\t")
        .append(String.format("%5d", element.getLineNumber()))
        .append(": '")
        .append(element.getFileName())
        .append("' >> ")
        .append(element.getClassName())
        .append(".")
        .append(element.getMethodName())
        .append("\n");
    }

    return result.toString();
  }
}

