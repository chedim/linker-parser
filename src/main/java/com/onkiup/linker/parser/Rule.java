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
   * @return parent token or null if this token is root token
   */
  default <R extends Rule> Optional<R> parent() {
    return Metadata.metadata(this)
      .map(meta -> {
        do {
          meta = (PartialToken) meta.parent().orElse(null);
        } while (!(meta instanceof RuleToken));
        return meta;
      })
      .flatMap(PartialToken::token);
  }

  /**
   * @return true if this token was successfully populated; false if parser is still working on some of the token's fields
   */
  default boolean populated() {
    return Metadata.metadata(this)
      .map(PartialToken::isPopulated)
      .orElse(false);
  }

  default void onPopulated() {

  }

  default Optional<PartialToken> metadata() {
    return Metadata.metadata(this);
  }

  default ParserLocation location() {
    return metadata().map(PartialToken::location).orElse(null);
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

  default CharSequence source() {
    return metadata().map(PartialToken::source).orElse(null);
  }
}

