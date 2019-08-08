package com.onkiup.linker.parser;

import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

// at 0.2.2:
// - added "C" type parameter
// - replaced all "evaluate" flags with context
public class TokenGrammar<C, X extends Rule<C>> {
  private Class<X> type;

  public static <C, X extends Rule<C>> TokenGrammar<C, X> forClass(Class<X> type) {
    return new TokenGrammar<>(type);
  }

  protected TokenGrammar(Class<X> type) {
    this.type = type;
  }

  public static boolean isConcrete(Class<?> test) {
    return !(test.isInterface() || Modifier.isAbstract(test.getModifiers()));
  }

  public Class<X> getTokenType() {
    return type;
  }

  public X parse(Reader source) throws SyntaxError {
    return parse(source, null);
  }

  public X parse(Reader source, C context)  throws SyntaxError {
    X result = tokenize(source, context);
    StringBuilder tail = new StringBuilder();
    try {
      int nextChar;
      while (-1 != (nextChar = source.read())) {
        tail.append((char) nextChar);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    if (tail.length() > 0) {
      throw new RuntimeException("Unmatched trailing symbols: '" + tail + "'");
    }
    return result;
  }

  public X tokenize(Reader source, C context) throws SyntaxError {
    try {
      ConcurrentLinkedDeque<PartialToken<C, ?>> trace = new ConcurrentLinkedDeque<>();
      trace.push(new PartialToken<C, X>(type));
      int nextChar;
      StringBuilder buffer = new StringBuilder();

      do {
        if (buffer.length() > 0) {
          PartialToken<C, ?> token = trace.peekFirst();
          Class type = token.getTokenType();
          Field field = token.getCurrentField();
          TokenTestResult testResult = null;

          if (Rule.class.isAssignableFrom(type)) {
            PartialToken child = new PartialToken(field.getType());
            trace.addFirst(child);
            continue;
          } else if (Number.class.isAssignableFrom(type)){
            TokenMatcher matcher = token.getMatcher();
            if (matcher == null) {
              matcher = new NumberMatcher((Class<? extends Number>) type);
              token.setMatcher(matcher);
            }
            testResult = matcher.apply(buffer);
          } else if (String.class.isAssignableFrom(type)) {
            TokenMatcher matcher = token.getMatcher();
            if (matcher == null) {
              if (field == null) {
                throw new RuntimeException("Unable to read directly to String");
              }
              if (Modifier.isStatic(field.getModifiers())) {
                Object pattern = field.get(null);
                if (pattern != null) {
                  matcher = new TerminalMatcher(pattern.toString());
                }
              } else if (field.isAnnotationPresent(CapturePattern.class)) {
                CapturePattern pattern = field.getAnnotation(CapturePattern.class);
                matcher = new PatternMatcher(pattern);
              }
              token.setMatcher(matcher);
            }
            testResult = matcher.apply(buffer);
          } else if (!isConcrete(type)) {
            Class variant = token.getCurrentAlternative();
            trace.addFirst(new PartialToken(variant));
            continue;
          } else if (type.isArray()) {
            Class elementType = type.getComponentType();
            trace.addFirst(new PartialToken(elementType));
            continue;
          } else {
            throw new RuntimeException("Unsupported token type: " + type);
          }

          if (testResult.isMatch()) {
            Object value = testResult.getToken();
            token.populateField(value);
            buffer = new StringBuilder(buffer.substring(testResult.getTokenLength()));
          } else if (testResult.isFailed()){
            trace.removeFirst();
            PartialToken parent = trace.peekFirst();
            parent.advanceAlternative();
            if (!parent.hasAlternativesLeft()) {
              throw new SyntaxError("Expected " +
                  ((field == null) ? type : field) +
                " but got <[" + buffer + "]>"
              );
            }
          }
          
          // tracing back
          while (token.isPopulated()) {
            PartialToken<C, ?> parent = (PartialToken<C, ?>) trace.removeFirst();
            if (isConcrete(parent.getTokenType())) {
              // token evaluation happens here
              field = token.getCurrentField();
              Object result = token.finalize(context);

              token = trace.peekFirst();
              if (token == null) {
                return (X) result;
              }
              field = token.getCurrentField();

              if (token.getTokenType().isArray()) {
                token.add(result);
                if (field != null) {
                  CaptureLimit limit = field.getAnnotation(CaptureLimit.class);
                  if (limit != null && limit.max() == token.getCollectionSize()) {
                    token.setPopulated();
                  }
                }
              } else {
                token.populateField(result);
              }
            }
          }
        } 

        nextChar = source.read();
        if (nextChar > 0) {
          buffer.append((char) nextChar);
        } else if (testResult != null && testResult.isContinue()) {
          
        }
      } while(buffer.size() > 0);
    } catch (SyntaxError se) {
      throw se;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

