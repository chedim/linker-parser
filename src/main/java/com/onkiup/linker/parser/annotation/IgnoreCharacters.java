package com.onkiup.linker.parser.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Instructs the parser to ignore provided characters before matching every field of the rule
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreCharacters {
  /**
   * @return string with characters to ignore
   */
  String value() default "";

  /**
   * @return a flag that indicates that parser should also use ignored charcters list from the parent token
   */
  boolean inherit() default false;
}

