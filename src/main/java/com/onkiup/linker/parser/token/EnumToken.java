package com.onkiup.linker.parser.token;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.rmi.CORBA.Util;

import com.google.common.annotations.VisibleForTesting;
import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.PatternMatcher;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TerminalMatcher;
import com.onkiup.linker.parser.TestResult;
import com.onkiup.linker.parser.TokenMatcher;
import com.onkiup.linker.parser.TokenTestResult;
import com.onkiup.linker.parser.annotation.CapturePattern;
import com.onkiup.linker.parser.util.ParserError;
import com.onkiup.linker.parser.util.Utils;

/**
 * Partial token used to populate Enum fields
 * TODO: test
 * @param <X>
 */
public class EnumToken<X extends Enum & Rule> extends AbstractToken<X> implements ConsumingToken<X>, Serializable {

  private Class<X> enumType;
  private transient Map<X,TokenMatcher> variants = new HashMap<>();
  private X token;
  private List<X> variantKeys;
  private int currentKeyIndex = 0;

  public EnumToken(CompoundToken parent, Field field, Class<X> enumType, ParserLocation location) {
    super(parent, field, location);
    this.enumType = enumType;
    boolean ignoreCaseFromTarget = Utils.ignoreCase(field);

    for (X variant : enumType.getEnumConstants()) {
      try {
        Field variantField = enumType.getDeclaredField(variant.name());
        CapturePattern annotation = variantField.getAnnotation(CapturePattern.class);
        boolean ignoreCase = ignoreCaseFromTarget || Utils.ignoreCase(variantField);
        TokenMatcher matcher = annotation == null ? new TerminalMatcher(variant.toString(), ignoreCase) : new PatternMatcher(annotation, ignoreCase);
        variants.put(variant, matcher);
      } catch (ParserError pe) {
        throw pe;
      } catch (Exception e) {
        throw new ParserError("Failed to read field for enum value " + variant, this, e);
      }
    }

    variantKeys = new ArrayList<>(variants.keySet());

    setTokenMatcher(buffer -> {
      if (variantKeys.size() == 0) {
        return TestResult.fail();
      }

      TokenTestResult result;
      do {
        TokenMatcher variantMatcher = variants.get(variantKeys.get(currentKeyIndex));
        result = variantMatcher.apply(buffer);
        if (result.isFailed()) {
          if (++currentKeyIndex < variantKeys.size()) {
            result = null;
          } else {
            return result;
          }
        }
      } while (result == null);

      return result;
    });
  }

  @VisibleForTesting
  void reset() {
    currentKeyIndex = 0;
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
  public void atEnd() {

  }

  @Override
  public void onConsumeSuccess(Object value) {
    token = variantKeys.get(currentKeyIndex);
  }

  /**
   * Handler that will be invoked upon token matching failure
   */
  @Override
  public void onFail() {
    super.onFail();
    reset();
  }

  /**
   * Handler for token population event
   *
   * @param end location after the last character matched with this token
   */
  @Override
  public void onPopulated(ParserLocation end) {
    super.onPopulated(end);
    reset();
  }
}

