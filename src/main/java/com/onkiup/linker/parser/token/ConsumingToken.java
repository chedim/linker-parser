package com.onkiup.linker.parser.token;

import java.util.Optional;
import java.util.WeakHashMap;
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

  void onConsumeSuccess(Object token) {
    parent().ifPresent(CompoundToken::onChildPopulated);
  }


  /**
   * Attempts to consume next character
   * @return null if character was consumed, otherwise returns a CharSequence with failed characters
   */
  default CharSequence consume(char character, boolean last) {
    ConsumptionState consumption = ConsumptionState.of(this);
    consumption.consume(character);

    if (isPopulated() || consumption.failed()) {
      if (!lookahead(consumption.buffer())) {
        onFail();
        // not accepting new characters at this time
        return parent()
          .map(CompoundToken::traceback)
          .map(StringBuilder::new)
          .map(sb -> sb.append(consumption.consumed()))
          .orElseGet(consumption::consumed);
      }
      // performing lookahead so, reporting successfull consumption (despite that the token has already failed)
      return null;
    }

    TokenTestResult result = consumption.test();

    if (result.isFailed() || (result.isContinue() && last)) {
      // switching to lookahead mode
      consumption.setFailed();
      return null;
    } else if (result.isMatch() || (result.isMatchContinue() && last)) {
      int tokenLength = result.getTokenLength();
      StringBuilder buffer = consumption.buffer();
      CharSequence excess = buffer.substring(tokenLength);
      onConsumeSuccess(result.getToken());
      onPopulated(consumption.end());
      return excess;
    }

    return null;
  }

  @Override
  default void invalidate() {
    PartialToken.super.invalidate();
    ConsumptionState.discard(this);
  }

  @Override
  default CharSequence source() {
    if (isFailed()) {
      return "";
    }
    return ConsumptionState.of(this).consumed();
  }

  class ConsumptionState {
    private static final ConcurrentHashMap<ConsumingToken, ConsumptionState> states = new ConcurrentHashMap<>();

    private static synchronized ConsumptionState of(ConsumingToken token) {
      if (!states.containsKey(token)) {
        throw new ParserError("No consumption state available for token " + token + " (create one by calling ConsumingToken::setMatcher first?)", token);
      }
      return states.get(token);
    }

    private static void create(ConsumingToken token, CharSequence ignoredCharacters, Function<CharSequence, TokenTestResult> tester) {
      states.put(token, new ConsumptionState(ignoredCharacters, tester));
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

    protected StringBuilder buffer() {
      return buffer;
    }

    protected StringBuilder consumed() {
      return consumed;
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
  }
}

