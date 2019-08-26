package com.onkiup.linker.parser.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be used on String fields to define capturing terminal limits
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CapturePattern {
  /**
   * Accepts a regular expression that will be used to match characters from the input
   * If provided then "until" parameter will be ignored
   */
  String value() default "";

  /**
   * Deprecated, use value instead
   */
  @Deprecated
  String pattern() default "";

  /**
   * Accepts a regular expression replacement parameter that can be used either to:
   *  - transform matched by defined as "value()" regexp text
   *  - transform matched by "until()" limiter and append transformation result to the end of captured text
   */
  String replacement() default "";

  /**
   * Accepts a regular expression that Parser will use as stop token for capturing process
   * If no "replacement()" is specified, then matched by this expression stop token will be discarded
   * If "replacement()" is specified, then stop token will be transformed using that value and appended to captured text
   * Ignored if either "value()" or "pattern()" are not empty
   */
  String until() default "";
}

