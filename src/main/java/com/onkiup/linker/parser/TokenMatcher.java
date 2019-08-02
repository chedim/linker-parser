package com.onkiup.linker.parser;

import java.util.function.Function;

@FunctionalInterface
public interface TokenMatcher extends Function<StringBuilder, TokenTestResult> {

}

