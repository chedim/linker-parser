package com.onkiup.linker.parser.token;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.PatternMatcher;
import com.onkiup.linker.parser.TestResult;
import com.onkiup.linker.parser.TokenTestResult;
import com.onkiup.linker.parser.annotation.CapturePattern;
import com.onkiup.linker.parser.util.ParserError;

public class EnumToken<X extends Enum> implements ConsumingToken<X> {

  private Class<X> enumType;
  private int nextVariant = 0;
  private CompoundToken<?> parent;
  private Field field;
  private ParserLocation location, end;
  private Map<X, PatternMatcher> variants = new HashMap<>();
  private X token;
  private boolean failed, optional, populated;
  private String ignoreCharacters;

  public EnumToken(CompoundToken<?> parent, Field field, Class<X> enumType, ParserLocation location) {
    this.enumType = enumType;
    this.location = location;
    this.parent = parent;
    this.field = field;

    for (X variant : enumType.getEnumConstants()) {
      try {
        Field variantField = enumType.getDeclaredField(variant.name());
        CapturePattern annotation = field.getAnnotation(CapturePattern.class);
        if (annotation == null) {
          throw new ParserError("Unable to use enum value " + variant + ": no @CapturePattern present", this);
        }
        PatternMatcher matcher = new PatternMatcher(annotation);
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
      for (Map.Entry<X, PatternMatcher> entry : variants.entrySet()) {
        TokenTestResult result = entry.getValue().apply(buffer);
        if (result.isMatch()) {
          return new TestResult.match(result.getTokenLength(), entry.getKey());
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
  public void onPopulated(ParserLocation end) {
    this.end = end;
    this.populated = true;
  }

  @Override 
  public void markOptional() {
    this.optional = true;
  }

  @Override
  public ParserLocation location() {
    return location;
  }

  @Override
  public ParserLocation end() {
    return end != null ? end : location;
  }

  @Override
  public Optional<Field> targetField() {
    return Optional.ofNullable(field);
  }

  @Override
  public X token() {
    return token;
  }

  @Override
  public Class<X> tokenType() {
    return enumType;
  }

  @Override
  public void onConsumeSuccess(Object value) {
    token = (X) value;
  }

  @Override
  public boolean isPopulated() {
    return token != null;
  }

  @Override
  public boolean isFailed() {
    return failed;
  }

  @Override
  public boolean isOptional() {
    return optional;
  }

  @Override
  public Optional<CompoundToken<?>> parent() {
    return Optional.ofNullable(parent);
  }
}

