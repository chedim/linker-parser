package com.onkiup.linker.parser;

import java.io.Reader;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ParserState {
  private Reader source;
  private StringBuilder buffer = new StringBuilder();
  private StringBuilder lineSource = new StringBuilder();
  private int line, col, lastCharCode;
  private String ignoredCharacters;
  private String sourceName;
  private ConcurrentLinkedDeque<PartialToken<?, ?>> trace = new ConcurrentLinkedDeque<>();

  public ParserState(String sourceName, Reader source, String ignoredCharacters) {
    this.sourceName = sourceName;
    this.source = source;
    this.ignoredCharacters = ignoredCharacters;
  }

  public ParserState(Reader source, String ignoredCharacters) {
    this(null, source, ignoredCharacters);
  }

  public ParserState(Reader source) {
    this(source, "");
  }

  public boolean advance() {
    try {
      lastCharCode = source.read();
      if (lastCharCode < 0) {
        return false;
      }

      char character = (char) lastCharCode;

      if (character == '\n') {
        line++;
        col = 0;
        lineSource = new StringBuilder();
      } else {
        lineSource.append(character);
        col++;
      }
      
      if (ignoredCharacters.indexOf(character) < 0) {
        buffer.append(character);
      }

      return true;
    } catch (Exception e) {
      throw new RuntimeException(toString(), e);
    }
  }

  public void drop(int characters) {
    buffer = new StringBuilder(buffer.substring(characters));
  }

  public String lineSource() {
    return lineSource.toString();
  }

  public StringBuilder buffer() {
    return buffer;
  }

  public int buffered() {
    return buffer.length();
  }

  public boolean empty() {
    return buffer.length() == 0;
  }
  
  public int line() {
    return line;
  }

  public int col() {
    return col;
  }

  public ParserLocation location() {
    return new ParserLocation(sourceName, lineSource.toString(), line, col);
  }

  public void push(PartialToken token) {
    trace.push(token);
  }

  public PartialToken token() {
    return trace.peekFirst();
  }

  public PartialToken pop() {
    return trace.pollFirst();
  }

  public int depth() {
    return trace.size();
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    PartialToken token = token();
    Field field = token == null ? null : token.getCurrentField();
    TokenMatcher matcher = token == null ? null : token.getMatcher();
    result.append('\n')
      .append("---[Parser state  ]---\n");
    if (field != null) {
      result.append("Field: ")
        .append(field)
        .append('\n');
    }
    if (matcher != null) {
      result.append("Matcher: ")
        .append(matcher)
        .append('\n');
    }
    if (sourceName != null) {
      result.append(sourceName);
      result.append(':');
      result.append(line);
      result.append(':');
      result.append(col);
      result.append('\n');
    } else {
      result.append("line: ").append(line).append('\n')
        .append("row: ").append(col).append('\n');
    }
    result.append("---[current line  ]---\n");
    result.append(lineSource).append('\n');
    result.append("---[current buffer]---\n");
    result.append(buffer).append('\n');
    result.append("---[current token ]---\n");
    Object[] branch = trace.toArray();
    for (int i = 0; i < branch.length; i++) {
      result.append(String.format("%-5s", i+": "))
        .append(branch[i])
        .append('\n');
    }
    result.append("----------------------");
    return result.toString();
  }
}

