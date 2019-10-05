package com.onkiup.linker.parser.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Instructs {@link com.onkiup.linker.parser.token.VariantToken} instances to use provided list of alternatives instead of generating it using Reflections
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Alternatives {
  /**
   * @return an array with alternatives to use
   */
  Class[] value();
}

