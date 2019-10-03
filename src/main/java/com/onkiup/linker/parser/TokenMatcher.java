package com.onkiup.linker.parser;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Function;

import com.onkiup.linker.parser.annotation.CapturePattern;
import com.onkiup.linker.parser.annotation.ContextAware;
import com.onkiup.linker.parser.token.CompoundToken;
import com.onkiup.linker.parser.util.LoggerLayout;

@FunctionalInterface
public interface TokenMatcher extends Function<CharSequence, TokenTestResult> {

  public static TokenMatcher forField(CompoundToken<?> parent, Field field) {
    Class type = field.getType();
    return forField(parent, field, type);
  }
  
  public static TokenMatcher forField(CompoundToken<?> parent, Field field, Class type) {
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
      } else if (field.isAnnotationPresent(ContextAware.class)) {
        ContextAware contextAware = field.getAnnotation(ContextAware.class);
        if (contextAware.matchField().length() > 0) {
          Object token = parent.token().orElseThrow(() -> new IllegalStateException("Parent token is null"));
          Field dependency = field.getDeclaringClass().getDeclaredField(contextAware.matchField());
          dependency.setAccessible(true);
          Object fieldValue = dependency.get(token);
          if (fieldValue instanceof String) {
            parent.log("Creating context-aware matcher for field $" + field.getName() + " to be equal to '" +
                LoggerLayout.sanitize(fieldValue) + "' value of target field $" + dependency.getName());
            return new TerminalMatcher((String)fieldValue);
          } else if (fieldValue == null) {
            parent.log("Creating context-aware null matcher for field $" + field.getName() + " to be equal to null value of target field $" + dependency.getName());
            return new NullMatcher();
          } else {
            throw new IllegalArgumentException("Unable to create field matcher for target field value of type '" + fieldValue.getClass().getName() + "'");
          }
        } else {
          throw  new IllegalArgumentException("Misconfigured ContextAware annotation?");
        }
      } else {
        throw new IllegalArgumentException("Non-static String fields MUST have CapturePattern annotation");
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to create matcher for field " + field, e);
    }
  }

}

