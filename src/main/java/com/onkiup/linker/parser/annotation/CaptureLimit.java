package com.onkiup.linker.parser.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits number of elements (array members, characters, etc) to be captured into the annotated field
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CaptureLimit {
  /**
   * @return Minimum number of elements required for the token to be populated
   */
  int min() default 0;

  /**
   * @return Maximum number of elements allowed to be populated into the token for it to not fail
   */
  int max() default Integer.MAX_VALUE;
}
