package com.onkiup.linker.parser.token;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.TestResult;
import com.onkiup.linker.parser.TokenMatcher;
import com.onkiup.linker.parser.TokenTestResult;
import com.onkiup.linker.parser.util.ParserError;

public interface ConsumingToken<X> extends PartialToken<X> {

  default void setTokenMatcher(TokenMatcher matcher) {
    CharSequence ignoredCharacters = parent().map(CompoundToken::ignoredCharacters).orElse(null);
    ConsumptionState.create(this, ignoredCharacters, matcher);
  }

  void onConsumeSuccess(Object token);

  /**
   * Attempts to consume next character
   * @return null if character was consumed, otherwise returns a CharSequence with failed characters
   */
  default Optional<CharSequence> consume(char character) {
    ConsumptionState consumption = ConsumptionState.of(this).orElseThrow(() -> new ParserError("No consumption state found (call ConsumingToken::setTokenMatcher to create it first)", this));

    consumption.consume(character);

    if (consumption.failed()) {
      if (!lookahead(consumption.buffer())) {
        log("Lokahead complete");
        onFail();
        CharSequence consumed = consumption.consumed();
        consumption.clear();
        return Optional.of(consumed);
      }
      log("performing lookahead so, reporting successfull consumption (despite that the token has already failed)");
      return Optional.empty();
    }

    TokenTestResult result = consumption.test();

    if (result.isFailed()) {
      log("failed; switching to lookahead mode");
      consumption.setFailed();
      return Optional.empty();
    } else if (result.isMatch()) {
      int tokenLength = result.getTokenLength();
      CharSequence excess = consumption.trim(tokenLength);
      log("matched");
      onConsumeSuccess(result.getToken());
      onPopulated(location().add(consumption.end()));
      parent()
          .filter(p -> p.unfilledChildren() == 0)
          .map(p -> p.lookahead(excess));
      return Optional.of(excess);
    }

    if (result.isMatchContinue()) {
      log("matched; continuing...");
      onConsumeSuccess(result.getToken());
      onPopulated(consumption.end());
    }

    return Optional.empty();
  }

  @Override
  default void invalidate() {
    PartialToken.super.invalidate();
    ConsumptionState.discard(this);
  }

  @Override
  default Optional<CharSequence> traceback() {
    return ConsumptionState.of(this).map(ConsumptionState::consumed)
        .filter(consumed -> consumed.length() > 0);
  }

  @Override
  default CharSequence source() {
    if (isFailed()) {
      return "";
    }
    return ConsumptionState.of(this).map(ConsumptionState::consumed).orElse("");
  }

  class ConsumptionState {
    private static final ConcurrentHashMap<ConsumingToken, ConsumptionState> states = new ConcurrentHashMap<>();

    private static synchronized Optional<ConsumptionState> of(ConsumingToken token) {
      return Optional.ofNullable(states.get(token));
    }

    private static void create(ConsumingToken token, CharSequence ignoredCharacters, Function<CharSequence, TokenTestResult> tester) {
      states.put(token, new ConsumptionState(ignoredCharacters, tester));
    }

    static void inject(ConsumingToken token, ConsumptionState state) {
      states.put(token, state);
    }

    private static void discard(ConsumingToken token) {
      states.remove(token);
    }

    private final StringBuilder buffer = new StringBuilder();
    private final StringBuilder consumed = new StringBuilder();
    private final CharSequence ignoredCharacters;
    private final Function<CharSequence, TokenTestResult> tester;
    private ParserLocation end;
    private boolean failed;

    private ConsumptionState(CharSequence ignoredCharacters, Function<CharSequence, TokenTestResult> tester) {
      this.ignoredCharacters = ignoredCharacters;
      this.tester = tester;
    }

    ConsumptionState(CharSequence buffer, CharSequence consumed) {
      this.ignoredCharacters = "";
      this.tester = null;
      this.buffer.append(buffer);
      this.consumed.append(consumed);
    }

    protected CharSequence buffer() {
      return buffer.subSequence(0, buffer.length());
    }

    protected CharSequence consumed() {
      return consumed.subSequence(0, consumed.length());
    }

    protected ParserLocation end() {
      return ParserLocation.endOf(consumed());
    }

    private boolean ignored(int character) {
      return ignoredCharacters != null && ignoredCharacters.chars().anyMatch(ignored -> ignored == character);
    }

    private void consume(char character) {
      consumed.append(character);
      if (buffer.length() > 0 || !ignored(character)) {
        buffer.append(character);
      }
    }

    private TokenTestResult test() {
      if (buffer.length() == 0) {
        return TestResult.continueNoMatch();
      }
      return tester.apply(buffer);
    }

    private void setFailed() {
      failed = true;
    }

    private boolean failed() {
      return failed;
    }

    private CharSequence trim(int size) {
      CharSequence trimmed = buffer.subSequence(size, buffer.length());
      buffer.delete(size, buffer.length());
      consumed.delete(consumed.length() - trimmed.length(), consumed.length());
      return trimmed;
    }

    private void clear() {
      consumed.delete(0, consumed.length());
      buffer.delete(0, buffer.length());
    }
  }
}

