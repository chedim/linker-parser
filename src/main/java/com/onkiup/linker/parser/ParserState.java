package com.onkiup.linker.parser;

import java.io.Reader;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParserState {
  private static Logger logger = LoggerFactory.getLogger(ParserState.class);
  private Reader source;
  private StringBuilder buffer = new StringBuilder();
  private StringBuilder lineSource = new StringBuilder();
  private int line, col, lastCharCode;
  private String sourceName;
  private ConcurrentLinkedDeque<PartialToken<?>> trace = new ConcurrentLinkedDeque<>();
  private Set<Class<? extends Rule>> testedAlternatives = new HashSet<>();
 
  public ParserState(String sourceName, Reader source) {
    this.sourceName = sourceName;
    this.source = source;
  }

  public ParserState(Reader source) {
    this("", source);
  }

  public boolean advance() {
    try {
      lastCharCode = source.read();
      logger.info("Advancing to character '"+ lastCharCode + "' (" + line + ':' + col + ")");
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
      buffer.append((char)character);
      logger.info("Buffer: '"+buffer+"'");
      
      return true;
    } catch (Exception e) {
      throw new RuntimeException(toString(), e);
    }
  }

  public String drop(int characters) {
    String dropped = buffer.substring(0, characters);
    buffer = new StringBuilder(buffer.substring(characters));
    if (characters > 0) {
      testedAlternatives.clear();
    }
    return dropped;
  }

  public void returnToBuffer(StringBuilder characters ) {
    buffer = new StringBuilder(characters.toString()).append(buffer.toString());
    if (characters.length() > 0) {
      testedAlternatives.clear();
    }
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
    if (trace.size() > 10) {
      throw new RuntimeException("Stack overflow");
    }
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

  public boolean hasAlternatives() {
    for (PartialToken token : trace) {
      if (token.hasAlternativesLeft()) {
        return true;
      }
    }
    
    return false;
  }

  public void registerAlternativeTest(Class<? extends Rule> alternative) {
    testedAlternatives.add(alternative);
  }

  public boolean discardAlternative() throws SyntaxError {
    PartialToken token = token();
    LinkedList<PartialToken> discarded = new LinkedList<>();

    PartialToken parent;
    boolean pickedAlternative = false;
    while (null != (parent = pop())) {
      if (parent.hasAlternatives()) {
        testedAlternatives.add(parent.getCurrentAlternative());
      }
      if (parent.hasAlternativesLeft()) {
        logger.info("Advancing to the next alternative for " + parent.getTokenType().getSimpleName() + "(discarded alternatives: "+
            testedAlternatives.stream().map(Class::getSimpleName).collect(Collectors.joining(", ")) + ")");
        Class<? extends Rule> nextAlternative = null;
        do {
          if (nextAlternative != null) {
            logger.info("Discarding already tested alternative: " + nextAlternative.getSimpleName());
          }
          nextAlternative = parent.advanceAlternative(lineSource());
          if (testedAlternatives.contains(nextAlternative)) {
            nextAlternative = null;
          }
        } while (nextAlternative == null && parent.hasAlternativesLeft());

        if (nextAlternative != null) {
          logger.info("Next alternative: " + nextAlternative);
          testedAlternatives.add(nextAlternative);
          push(parent);
          pickedAlternative = true;
          token = parent;
          break;
        }
      } else if (parent.isFieldOptional()) {
        logger.info("Ignoring optional field...");
        parent.populateField(null);
        push(parent);
        token = parent;
        pickedAlternative = true;
        break;
      } else if (parent.getTokenType().isArray()) {
        logger.info("Finished populating array '" + token +"'");
        pickedAlternative = true;
        parent.setPopulated();
        push(parent);
        token = parent;
        break;
      }
      logger.info("Discarging: " + parent);
      StringBuilder taken = parent.getTaken();
      logger.info("Returning taken: '" + taken + "'");
      returnToBuffer(taken);
      parent.discard();
      discarded.addFirst(parent);
    }
  
    if (!pickedAlternative) {
      discarded.forEach(this::push);
      if (parent == null) {
        throw new SyntaxError("Expected " + token().getTokenType() + " with matcher " + token().getMatcher(), this);
      } else {
        throw new SyntaxError("Expected " + parent, this);
      }
    }

    return true;
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
    String ignoreCharacters = token.getIgnoreCharacters();
    if (ignoreCharacters != null && ignoreCharacters.length() > 0) {
      result.append("IgnoredCharacters: " + ignoreCharacters.chars().boxed().map(Object::toString).collect(Collectors.joining(", ")) + "\n");
    }
    if (empty()) {
      result.append("Empty: true\n");
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

  public boolean isInTrace(Class<?> type) {
    return trace.stream()
      .filter(partialToken -> partialToken.getTokenType() == type)
      .findFirst()
      .isPresent();
  }
}

