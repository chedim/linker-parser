package com.onkiup.linker.parser;

import java.io.IOException;
import java.io.Reader;
import java.util.Stack;
import java.util.function.Function;

public class NestingReader extends Reader {

  private Reader source;
  private StringBuilder buffer = new StringBuilder();
  private int position = 0;
  private Stack<Integer> positionStack = new Stack<>();

  public NestingReader(Reader source) {
    this.source = source;
  }

  @Override
  public void close() throws IOException {
    if (positionStack.size() == 0) {
      buffer = null;
      source.close();
    } else {
      throw new RuntimeException("Nested readers are still open");
    }
  }

  @Override
  public int read(char[] target, int offset, int len) throws IOException {
    int result = 0;
    boolean isTop = positionStack.isEmpty();
    if (position < buffer.length()) {
      int bufferLen = Math.min(len, buffer.length() - position);
      String bufferChars = buffer.substring(position);
      System.arraycopy(bufferChars.toCharArray(), 0, target, offset, bufferLen);
      offset += bufferLen;
      len -= bufferLen;
      result += bufferLen;
      if (isTop) {
        buffer = new StringBuilder(buffer.substring(bufferLen));
      }
    }

    if (len > 0) {
      char[] sourceChars = new char[len];
      int sourceLen = source.read(sourceChars, 0, len);
      if (sourceLen > -1) {
        System.arraycopy(sourceChars, 0, target, offset, sourceLen);
        if (!isTop) {
          buffer.append(sourceChars, 0, sourceLen);
        }
        result += sourceLen;
      } else if (result == 0) {
        return -1;
      }

    }

    if (!isTop) {
      position += result;
    }

    return result;
  }

  public void nest(Function<NestingReader, Boolean> code) {
    positionStack.push(position);
    boolean accept = false;
    try {
      accept = code.apply(this);
    } finally {
      if (accept) {
        positionStack.pop();
      } else {
        position = positionStack.pop();
      }

      if (positionStack.isEmpty() && position > 0) {
        // discard processed stream bytes
        buffer = new StringBuilder(buffer.substring(position));
        position = 0;
      }
    }
  }

  public void pushBack(char what) {
    buffer.insert(position, what);
  }

  public int getPositionOffset() {
    return position;
  }

  public int getNestingLevel() {
    return positionStack.size();
  }
}

