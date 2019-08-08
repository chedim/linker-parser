package com.onkiup.linker.parser;

public @interface CaptureLimit {
  int min() default 0;
  int max() default Integer.MAX_VALUE;
}
