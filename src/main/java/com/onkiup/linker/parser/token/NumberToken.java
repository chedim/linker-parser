package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.Optional;

import com.onkiup.linker.parser.NumberMatcher;
import com.onkiup.linker.parser.ParserLocation;

public class NumberToken<X extends Number> extends AbstractToken<X> implements ConsumingToken<X> {

  private X token;
  private Class<X> tokenType;

  public NumberToken(CompoundToken<?> parent, int position, Field targetField, ParserLocation location) {
    super(parent, position, targetField, location);
    this.tokenType = (Class<X>)targetField.getType();

    setTokenMatcher(new NumberMatcher(tokenType));
  }

  @Override
  public void onConsumeSuccess(Object token) {
    this.token = (X) token;
  }

  @Override
  public Optional<X> token() {
    return Optional.ofNullable(token);
  }

  /**
   * @return the type of resulting java token
   */
  @Override
  public Class<X> tokenType() {
    return tokenType;
  }

  /**
   * A callback that is invoked when token matching hits end of parser input
   * An invocation should result in either token failure or population
   */
  @Override
  public void atEnd() {
    if (token == null) {
      onFail();
    }
    onPopulated(end());
    ConsumingToken.super.atEnd();
  }
}
