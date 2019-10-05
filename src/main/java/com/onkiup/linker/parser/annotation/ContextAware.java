package com.onkiup.linker.parser.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows context-aware token matching
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ContextAware {
  /**
   * Instructs the parser to create a ConsumingToken for this field that would exactly match value from a previously populated field
   * @return
   */
  String matchField() default "";
}
