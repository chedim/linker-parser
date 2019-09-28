package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.PatternMatcher;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TerminalMatcher;
import com.onkiup.linker.parser.TestResult;
import com.onkiup.linker.parser.TokenMatcher;
import com.onkiup.linker.parser.TokenTestResult;
import com.onkiup.linker.parser.annotation.CapturePattern;
import com.onkiup.linker.parser.util.ParserError;

public class EnumToken<X extends Enum & Rule> extends AbstractToken<X> implements ConsumingToken<X> {

  private Class<X> enumType;
  private int nextVariant = 0;
  private Map<X,TokenMatcher> variants = new HashMap<>();
  private X token;
  private boolean failed, populated;
  private String ignoreCharacters;

  public EnumToken(CompoundToken parent, Field field, Class<X> enumType, ParserLocation location) {
    super(parent, field, location);
    this.enumType = enumType;

    for (X variant : enumType.getEnumConstants()) {
      try {
        Field variantField = enumType.getDeclaredField(variant.name());
        CapturePattern annotation = variantField.getAnnotation(CapturePattern.class);
        TokenMatcher matcher = annotation == null ? new TerminalMatcher(variant.toString()) : new PatternMatcher(annotation);
        variants.put(variant, matcher);
      } catch (ParserError pe) {
        throw pe;
      } catch (Exception e) {
        throw new ParserError("Failed to read field for enum value " + variant, this, e);
      }
    }

    setTokenMatcher(buffer -> {
      if (variants.size() == 0) {
        return TestResult.fail();
      }

      List<X> failed = new ArrayList<>();
      for (Map.Entry<X, TokenMatcher> entry : variants.entrySet()) {
        TokenTestResult result = entry.getValue().apply(buffer);
        if (result.isMatch()) {
          return TestResult.match(result.getTokenLength(), entry.getKey());
        } else if (result.isFailed()) {
          failed.add(entry.getKey());
        }
      }

      failed.forEach(variants::remove);

      if (variants.size() == 0) {
        return TestResult.fail();
      }

      return TestResult.continueNoMatch();
    });
  }

  @Override
  public Optional<X> token() {
    return Optional.ofNullable(token);
  }

  @Override
  public Class<X> tokenType() {
    return enumType;
  }

  @Override
  public void onConsumeSuccess(Object value) {
    token = (X) value;
    this.populated = true;
  }

}

