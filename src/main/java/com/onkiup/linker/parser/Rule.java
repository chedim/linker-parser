package com.onkiup.linker.parser;

import java.util.HashMap;
import java.util.function.Consumer;

// in 0.2.2:
// - added "C" type parameter
// - made it implement Consumer
public interface Rule<C> extends Consumer<C> {

  static class Metadata {
    private static HashMap<Rule, ParserLocation> ruleLocations = new HashMap<>();

    public static ParserLocation ruleLocation(Rule rule) {
      return ruleLocations.get(rule);
    }

    static void ruleLocation(Rule rule, ParserLocation location) {
      ruleLocations.put(rule, location);
    }
  }

  default void accept(C context) {
    throw new RuntimeException("Not implemented");
  }

  default String transpile() {
    throw new RuntimeException("Not implemented");
  }

  default ParserLocation location() {
    return Metadata.ruleLocation(this);
  }
}

