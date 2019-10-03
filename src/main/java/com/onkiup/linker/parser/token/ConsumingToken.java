package com.onkiup.linker.parser.token;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.swing.text.html.parser.Parser;

import org.apache.log4j.Layout;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TestResult;
import com.onkiup.linker.parser.TokenMatcher;
import com.onkiup.linker.parser.TokenTestResult;
import com.onkiup.linker.parser.util.LoggerLayout;
import com.onkiup.linker.parser.util.ParserError;

public interface ConsumingToken<X> extends PartialToken<X> {

  default void setTokenMatcher(TokenMatcher matcher) {
    ConsumptionState.create(this, matcher);
  }

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

  class ConsumptionState {
    private static final ConcurrentHashMap<ConsumingToken, ConsumptionState> states = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<PartialToken, CharSequence> buffers = new ConcurrentHashMap<>();

    private static synchronized Optional<ConsumptionState> of(ConsumingToken token) {
      return Optional.ofNullable(states.get(token));
    }

    private static void create(ConsumingToken token, Function<CharSequence, TokenTestResult> tester) {
      states.put(token, new ConsumptionState(token, tester));
    }

    static void inject(ConsumingToken token, ConsumptionState state) {
      states.put(token, state);
    }

    private static void discard(ConsumingToken token) {
      states.remove(token);
    }

    private final String ignoredCharacters;
    private final Function<CharSequence, TokenTestResult> tester;
    private ParserLocation start, end, ignored;
    private boolean failed;
    private ConsumingToken token;
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

    public static <X extends Rule> void rootBuffer(PartialToken<X> rootToken, CharSequence buffer) {
      buffers.put(rootToken, buffer);
    }

    public static Optional<CharSequence> rootBuffer(PartialToken<?> root) {
      return Optional.ofNullable(buffers.get(root));
    }

    protected CharSequence buffer() {
      return buffer.subSequence(ignored.position(), end.position());
    }

    protected CharSequence consumed() {
      return buffer.subSequence(start.position(), end.position());
    }

    protected ParserLocation end() {
      return end;
    }

    private boolean ignored(int character) {
      return ignoredCharacters != null && ignoredCharacters.chars().anyMatch(ignored -> ignored == character);
    }

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

    private boolean hitEnd() {
      return hitEnd;
    }

    private TokenTestResult test() {
      if (end.position() - ignored.position() == 0) {
        return TestResult.continueNoMatch();
      }
      return tester.apply(buffer());
    }

    private void setFailed() {
      failed = true;
    }

    private boolean failed() {
      return failed;
    }

    private void trim(int size) {
      end = ignored.advance(buffer().subSequence(0, size));
    }

    private void clear() {
      end = ignored = start;
    }

    private void lookahead() {
      token.lookahead(buffer, ignored.position());
      token.log("Lookahead complete");
      token.onFail();
    }

  }
}

