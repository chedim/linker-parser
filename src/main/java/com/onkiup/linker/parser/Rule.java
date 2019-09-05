package com.onkiup.linker.parser;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.onkiup.linker.parser.token.CollectionToken;
import com.onkiup.linker.parser.token.PartialToken;
import com.onkiup.linker.parser.token.RuleToken;
import com.onkiup.linker.parser.token.VariantToken;

// in 0.4:
// - changed Metadata to hold PartialTokens instead of ParserLocations
// in 0.2.2:
// - added "C" type parameter
// - made it implement Consumer
/**
 * Main interface for all grammar definitions
 */
public interface Rule {

  static class Metadata {
    private static ConcurrentHashMap<Rule, PartialToken> metadata = new ConcurrentHashMap<>();

    public static Optional<PartialToken> metadata(Rule rule) {
      return Optional.ofNullable(metadata.get(rule));
    }

    public static void metadata(Rule rule, PartialToken token) {
      metadata.put(rule, token);
    }

    static void remove(Rule rule) {
      metadata.remove(rule);
    }
  }

  /**
   * @returns parent token or null if this token is root token
   */
  default <R extends Rule> Optional<R> parent() {
    PartialToken meta = Metadata.metadata(this).get();
    do {
      meta = (PartialToken) meta.getParent().orElse(null);
    } while (meta != null && !(meta instanceof RuleToken));

    if (meta != null) {
      return Optional.of((R) meta.getToken());
    }
    return Optional.empty();
  }

  /**
   * @returns true if this token was successfully populated; false if parser is still working on some of the token's fields
   */
  default boolean populated() {
    return Metadata.metadata(this)
      .map(PartialToken::isPopulated)
      .orElse(false);
  }

  default PartialToken metadata() {
    return Metadata.metadata(this).get();
  }

  default ParserLocation location() {
    return metadata().location();
  }

  /**
   * Reevaluation callback.
   * Called by parser every time it updates the token
   */
  default void reevaluate() {
    
  }

  /**
   * Invalidation callback
   * called by arser every time it detaches the token from the tree
   */
  default void invalidate() {
  
  }
}

