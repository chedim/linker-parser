package com.onkiup.linker.parser.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.onkiup.linker.parser.TokenMatcher;

@Deprecated
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CustomMatcher {
  Class<? extends TokenMatcher> value();    
}

