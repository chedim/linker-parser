package com.onkiup.linker.parser.token;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;

import org.junit.Test;
import org.junit.validator.ValidateWith;
import org.mockito.Mockito;

import com.onkiup.linker.parser.ParserLocation;
import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.annotation.CapturePattern;

public class EnumTokenTest {

  public EnumTokenTest() throws NoSuchFieldException {
  }

  public static enum EttEnum implements Rule {
    ONE,
    TWO,
    THREE;
  }

  public static class EttWrapper {
    private EttEnum enumValue;
  }

  private Field enumField = EttWrapper.class.getDeclaredField("enumValue");

  @Test
  public void testParsing() {
    CompoundToken parent = Mockito.mock(CompoundToken.class);
    EnumToken<?> token = new EnumToken(parent, enumField, EttEnum.class, new ParserLocation(null, 0, 0, 0));
    String source = "TWO";

    for (int i = 0; i < source.length(); i++) {
      CharSequence returned = token.consume(source.charAt(i)).orElse(null);
      if (returned != null && returned.length() > 0) {
        fail("Unexpected buffer return at character#" + i + ": '" + returned);
      }
    }

    assertEquals(EttEnum.TWO, token.token().get());

    assertEquals("z", token.consume('z').get());
  }
}
