package com.onkiup.linker.parser;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CapturePattern {
  String pattern() default "";
  String replacement() default "";
  String until() default "";
}

