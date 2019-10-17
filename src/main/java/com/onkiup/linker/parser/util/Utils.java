package com.onkiup.linker.parser.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;

import com.onkiup.linker.parser.Rule;
import com.onkiup.linker.parser.TokenGrammar;
import com.onkiup.linker.parser.annotation.IgnoreCase;

public final class Utils {
  private Utils() {

  }

  public static Field[] getTokenFields(Class<? extends Rule> source) {
    // first, we need to iterate back to the "base" rule class
    Class type = source;
    do {
      type = type.getSuperclass();
    } while (Rule.class.isAssignableFrom(type) && TokenGrammar.isConcrete(type));

    return Arrays.stream(type.getDeclaredFields())
        .filter(childField -> !Modifier.isTransient(childField.getModifiers()))
        .toArray(Field[]::new);
  }

  public static boolean ignoreCase(Field forField) {
    return Optional.ofNullable(forField)
        .map(field -> field.isAnnotationPresent(IgnoreCase.class) ? field.getAnnotation(IgnoreCase.class) :
            field.getType().isAnnotationPresent(IgnoreCase.class) ? field.getDeclaringClass().getAnnotation(IgnoreCase.class) :
                null
        ).map(IgnoreCase::value).orElse(false);
  }
}
