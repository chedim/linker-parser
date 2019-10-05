package com.onkiup.linker.parser.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as optional
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OptionalToken {
  /**
   * Instructs the parser to treat this field as optional only if its possible position
   * in the source contains returned characters instead
   * @return characters to test for
   */
  String whenFollowedBy() default "";

  /**
   * Instructs the parser to treat this field as optional only when other (previously processed) field is null
   * @return the name of the other field to test
   */
  String whenFieldIsNull() default "";

  /**
   * Instructs the parser to treat this field as optional only when other (previously processed) field is not null
   * @return the name of the other field to test
   */
  String whenFieldNotNull() default "";
}

