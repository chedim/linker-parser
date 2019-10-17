package com.onkiup.linker.parser.token;

import java.io.Serializable;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TestResult;
import com.onkiup.linker.parser.TokenMatcher;
import com.onkiup.linker.parser.TokenTestResult;
import com.onkiup.linker.parser.util.LoggerLayout;
import com.onkiup.linker.parser.util.ParserError;

/**
 * Interfacde that represents any token that can advance parser by consuming characters from parser's buffer
 * @param <X> type of resulting token
 */
public interface ConsumingToken<X> extends PartialToken<X>, Serializable {

  /**
   * Provides TokenMatcher for default consumption algorithm
   * @param matcher the matcher to use against consumed characters
   */
  default void setTokenMatcher(TokenMatcher matcher) {
    ConsumptionState.create(this, matcher);
  }

  /**
   * Callback method invoked upon partial or full match against consumed characters
   * @param token resulting token, as provided by previously configured matcher
   */
  void onConsumeSuccess(Object token);

  /**
   * Attempts to consume next character
   * @return true if consumption should continue
   */
  default boolean consume() {
    ConsumptionState consumption = ConsumptionState.of(this).orElseThrow(() -> new ParserError("No consumption state found (call ConsumingToken::setTokenMatcher to create it first)", this));

    boolean doNext = consumption.consume();

    TokenTestResult result = consumption.test();

    if (result.isFailed()) {
      log("failed; switching to lookahead mode");
      consumption.setFailed();
      consumption.lookahead();
      consumption.clear();
      onFail();
      return false;
    } else if (result.isMatch()) {
      consumption.trim(result.getTokenLength());
      log("matched at position {}", consumption.end().position());
      onConsumeSuccess(result.getToken());
      onPopulated(consumption.end());
      return false;
    }

    if (result.isMatchContinue()) {
      log("matched; continuing...");
      onConsumeSuccess(result.getToken());
      onPopulated(consumption.end());
    } else if (consumption.hitEnd()) {
      onFail();
    }

    return doNext;
  }

  @Override
  default void invalidate() {
    PartialToken.super.invalidate();
    ConsumptionState.discard(this);
  }

  @Override
  default void atEnd() {
    parent().ifPresent(CompoundToken::atEnd);
  }

  @VisibleForTesting
  default Function<CharSequence, TokenTestResult> tokenMatcher() {
    return ConsumptionState.of(this).map(ConsumptionState::tester).orElse(null);
  }

  /**
   * A helper class that implements major parts of consumption algorithm and stores consumption states for ConsumingToken instances
   */
  class ConsumptionState {
    private static final ConcurrentHashMap<ConsumingToken, ConsumptionState> states = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<PartialToken, CharSequence> buffers = new ConcurrentHashMap<>();

    /**
     * Returns previously registered ConsumptionState for the given token
     * @param token token whose ConsumptionState should be returned
     * @return ConsumptionState instance for provided token
     */
    private static synchronized Optional<ConsumptionState> of(ConsumingToken token) {
      return Optional.ofNullable(states.get(token));
    }

    /**
     * Creates and registers a new ConsumptionState for the given token
     * @param token token for which a new ConsumptionState should be created
     * @param tester function that will be used to match consumed characters
     */
    private static void create(ConsumingToken token, Function<CharSequence, TokenTestResult> tester) {
      states.put(token, new ConsumptionState(token, tester));
    }

    /**
     * Register given ConsuptionState for given token
     * @param token a token for which given ConsumptionState should be registered
     * @param state ConsumptionState that sould be registered for the given token
     */
    static void inject(ConsumingToken token, ConsumptionState state) {
      states.put(token, state);
    }

    /**
     * Discards ConsumptionState registered for given token
     * @param token token whose ConsumptionState should be discarded
     */
    private static void discard(ConsumingToken token) {
      states.remove(token);
    }

    /**
     * List of characters to ignore at the beginning of consumption
     */
    private final String ignoredCharacters;
    /**
     * The tester used to match consumed characters
     */
    private final Function<CharSequence, TokenTestResult> tester;
    /**
     * Pointers to the buffer
     */
    private ParserLocation start, end, ignored;
    /**
     * Failure flag
     */
    private boolean failed;
    /**
     * the token with which this consumption is associated
     */
    private ConsumingToken token;
    /**
     * parser buffer
     */
    private CharSequence buffer;

    private boolean hitEnd = false;

    private ConsumptionState(ConsumingToken<?> token, Function<CharSequence, TokenTestResult> tester) {
      this.token = token;
      this.ignoredCharacters = token.ignoredCharacters();
      this.tester = tester;
      this.start = this.end = this.ignored = token.location();
      this.buffer = rootBuffer(token.root()).orElseThrow(() ->
          new RuntimeException("No root buffer registered for token " + token));
    }

    ConsumptionState(ParserLocation start, ParserLocation ignored, ParserLocation end) {
      this.ignoredCharacters = "";
      this.tester = null;
      this.start = start;
      this.end = end;
      this.ignored = ignored;
    }

    /**
     * Stores a parser buffer used to populate given AST root
     * @param rootToken AST root whose parser buffer should be stored
     * @param buffer buffer used to populate the given AST
     * @param <X>
     */
    public static <X extends Rule> void rootBuffer(PartialToken<X> rootToken, CharSequence buffer) {
      buffers.put(rootToken, buffer);
    }

    /**
     * @param root root token of the AST
     * @return parser buffer used to populate given AST
     */
    public static Optional<CharSequence> rootBuffer(PartialToken<?> root) {
      return Optional.ofNullable(buffers.get(root));
    }

    /**
     * @return consumed characters minus ignored prefix
     */
    protected CharSequence buffer() {
      return buffer.subSequence(ignored.position(), end.position());
    }

    /**
     * @return consumed characters, including ignored prefix
     */
    protected CharSequence consumed() {
      return buffer.subSequence(start.position(), end.position());
    }

    /**
     * @return location in parser's buffer immediately after the last consumed character or consumption start location when no characters were consumed
     */
    protected ParserLocation end() {
      return end;
    }

    /**
     * @param character character to test
     * @return true if provided character should be ignored and no non-ignorable characters were previously consumed
     */
    private boolean ignored(int character) {
      return ignoredCharacters != null && ignoredCharacters.chars().anyMatch(ignored -> ignored == character);
    }

    /**
     * Consumes the character at consumption's end location and advances that location if the character was consumed
     * @return true if consumption process can proceed to the next character or false if the consumption should be stopped
     */
    private boolean consume() {
      if (end.position() < buffer.length()) {
        char consumed = buffer.charAt(end.position());
        end = end.advance(consumed);
        if (end.position() - ignored.position() < 2 && ignored(consumed)) {
          ignored = ignored.advance(consumed);
          token.log("Ignored '{}' ({} - {} - {})", LoggerLayout.sanitize(consumed), start.position(), ignored.position(), end.position());
          return true;
        }
        token.log("Consumed '{}' ({} - {} - {})", LoggerLayout.sanitize(consumed), start.position(), ignored.position(), end.position());
        return true;
      } else {
        hitEnd = true;
      }
      return false;
    }

    /**
     * @return true if consumption ended at parser buffer's end
     */
    private boolean hitEnd() {
      return hitEnd;
    }

    /**
     * tests configured TokenMatcher against consumed characters (excluding ignored prefix)
     * @return reported by TokenMatcher test result structure
     */
    private TokenTestResult test() {
      if (end.position() - ignored.position() == 0) {
        return TestResult.continueNoMatch();
      }
      return tester.apply(buffer());
    }

    /**
     * Marks this consumption as failed
     */
    private void setFailed() {
      failed = true;
    }

    /**
     * @return true if this consumption was marked as failed
     */
    private boolean failed() {
      return failed;
    }

    /**
     * adjusts internal buffer pointers so that the number of consumed after ignored prefix characters appears to be equal to the given number
     * @param size the new size for consumption buffer
     */
    private void trim(int size) {
      end = ignored.advance(buffer().subSequence(0, size));
    }

    /**
     * reinitializes internal buffer pointers
     */
    private void clear() {
      end = ignored = start;
    }

    /**
     * performs lookahead on consumption's token
     */
    private void lookahead() {
      token.lookahead(buffer, ignored.position());
      token.log("Lookahead complete");
      token.onFail();
    }

    public Function<CharSequence, TokenTestResult> tester() {
      return tester;
    }

  }
}

