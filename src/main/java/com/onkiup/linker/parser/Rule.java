package com.onkiup.linker.parser;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

// in 0.4:
// - changed Metadata to hold PartialTokens instead of ParserLocations
// in 0.2.2:
// - added "C" type parameter
// - made it implement Consumer
public interface Rule {

  static class Metadata {
    private static ConcurrentHashMap<Rule, PartialToken> metadata = new ConcurrentHashMap<>();

    public static PartialToken metadata(Rule rule) {
      return metadata.get(rule);
    }

    static void metadata(Rule rule, PartialToken token) {
      metadata.put(rule, token);
    }

    static void remove(Rule rule) {
      metadata.remove(rule);
    }
  }

  default ParserLocation location() {
    return Metadata.metadata(this).getLocation();
  }

  default Rule parent() {
    PartialToken metadata = Metadata.metadata(this);
    if (metadata != null) {
      PartialToken parent = metadata.getParent();
      if (parent != null) {
        return (Rule) parent.getToken();
      }
    }
    return null;
  }

  default boolean populated() {
    PartialToken metadata = Metadata.metadata(this);
    if (metadata != null) {
      return metadata.isPopulated();
    }
    return false;
  }

  default void reevaluate() {
    
  }
}

