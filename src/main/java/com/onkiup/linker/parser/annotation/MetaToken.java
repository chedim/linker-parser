package com.onkiup.linker.parser.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a rule definition class as a MetaToken,
 * which causes VariantToken to "hide" matched instances of marked class by detaching them from the AST and
 * putting them into the next matched variant's metadata
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MetaToken {
}
