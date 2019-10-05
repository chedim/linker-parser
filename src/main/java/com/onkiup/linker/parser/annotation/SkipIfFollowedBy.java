package com.onkiup.linker.parser.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @see {@link OptionalToken#whenFollowedBy()}
 * @implNote current behaviour is similar to {@link OptionalToken#whenFollowedBy()} (the parser first tries to process the field and tests if its optional only when matching fails), but this may change later (so that the parser skips the field completely when optionality test succeeds without trying to match it)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SkipIfFollowedBy {
  String value();
}

