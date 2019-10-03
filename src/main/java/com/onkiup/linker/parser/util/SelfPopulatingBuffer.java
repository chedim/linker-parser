package com.onkiup.linker.parser.util;

import java.io.IOException;
import java.io.Reader;

public class SelfPopulatingBuffer implements CharSequence {

  private final StringBuilder buffer = new StringBuilder();
  private final String name;

  public SelfPopulatingBuffer(String name, Reader reader) throws IOException {
    this.name = name;
    for (int character = reader.read(); character > -1; character = reader.read()) {
      buffer.append((char)character);
    }
  }

  public String name() {
    return name;
  }

  @Override
  public int length() {
    return buffer.length();
  }

  @Override
  public char charAt(int index) {
    return buffer.charAt(index);
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return buffer.subSequence(start, end);
  }

  @Override
  public String toString() {
    return buffer.toString();
  }
}
