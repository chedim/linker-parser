package com.onkiup.linker.parser;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Function;

import com.onkiup.linker.parser.annotation.CapturePattern;

@FunctionalInterface
public interface TokenMatcher extends Function<CharSequence, TokenTestResult> {
  
  public static TokenMatcher forField(Field field) {
    Class type = field.getType();
    if (type.isArray()) {
      throw new IllegalArgumentException("Array fields should be handled as ArrayTokens");
    } else if (Rule.class.isAssignableFrom(type)) {
      throw new IllegalArgumentException("Rule fields should be handled as RuleTokens");
    } else if (type != String.class) {
      throw new IllegalArgumentException("Unsupported field type: " + type);
    }

    try {
      field.setAccessible(true);
      if (Modifier.isStatic(field.getModifiers())) {
        String terminal = (String) field.get(null);
        if (terminal == null) {
          throw new IllegalArgumentException("null terminal");
        }

        return new TerminalMatcher(terminal);
      } else if (field.isAnnotationPresent(CapturePattern.class)) {
        CapturePattern pattern = field.getAnnotation(CapturePattern.class);
        return new PatternMatcher(pattern);
      } else {
        throw new IllegalArgumentException("Non-static String fields MUST have CapturePattern annotation");
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to create matcher for field " + field, e);
    }
  }

}

