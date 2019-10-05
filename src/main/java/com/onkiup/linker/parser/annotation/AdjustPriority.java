package com.onkiup.linker.parser.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adjusts concrete token priority that affects token testing order for grammar junctions ({@link com.onkiup.linker.parser.token.VariantToken})
 * (tokens tested in ascending order of their priority: token with priority 0 will be tested prior to token with priority 9999)
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdjustPriority {
  /**
   * @return value to which token's priority should be adjusted
   */
  int value();

  /**
   * @return boolean flag that indicates whether this priority adjustment should be propagated to parent token
   * (used primarily for arithmetical equations)
   */
  boolean propagate() default false;
}

