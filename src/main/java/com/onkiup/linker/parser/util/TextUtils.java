package com.onkiup.linker.parser.util;

import com.onkiup.linker.parser.token.PartialToken;

public interface TextUtils {
  static CharSequence removeIgnoredCharacters(PartialToken<?> token, CharSequence from) {
    String ignoredCharacters = token.ignoredCharacters();
    token.log("Removing ignored characters '{}' from '{}'", LoggerLayout.sanitize(ignoredCharacters), LoggerLayout.sanitize(from));
    if (ignoredCharacters.length() == 0) {
      return from;
    }
    for (int i = 0; i < from.length(); i++) {
      if (ignoredCharacters.indexOf(from.charAt(i)) < 0) {
        return from.subSequence(i, from.length());
      }
    }
    return "";
  }

  static int firstNonIgnoredCharacter(PartialToken<?> token, CharSequence buffer, int from) {
    String ignoredCharacters = token.ignoredCharacters();
    char character;
    do {
      character = buffer.charAt(from++);
    } while (ignoredCharacters.indexOf(character) > -1);
    return from - 1;
  }
}
