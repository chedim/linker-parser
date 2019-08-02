package com.onkiup.linker.parser;

import java.util.function.Function;

@FunctionalInterface
public interface Matcher extends Function<StringBuilder, TokenTestResult> {

}

