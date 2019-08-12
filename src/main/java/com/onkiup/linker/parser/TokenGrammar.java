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
      TokenTestResult testResult = null;

      do {
        PartialToken<C, ?> token = trace.peekFirst();
        Class type = token.getTokenType();
        Field field = token.getCurrentField();
        TokenMatcher matcher = token.getMatcher();

        if (buffer.length() > 0) {
          if (matcher == null) {
            if (type.isArray()) {
              trace.addFirst(new PartialToken(type.getComponentType()));
              continue;
            } if (!isConcrete(type)) {
              Class variant = token.getCurrentAlternative();
              trace.addFirst(new PartialToken(variant));
              continue;
            } else if (Rule.class.isAssignableFrom(type)) {
              Class fieldType = field.getType() ;
              PartialToken child;
              if (fieldType.isArray()) {
                child = new PartialToken(fieldType);
              } else {
                child = new PartialToken(field.getType());
                if (String.class.isAssignableFrom(fieldType)) {
                  if (Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    String pattern = (String) field.get(null);
                    child.setMatcher(new TerminalMatcher(pattern));
                  } else if (field.isAnnotationPresent(CapturePattern.class)) {
                    CapturePattern pattern = field.getAnnotation(CapturePattern.class);
                    child.setMatcher(new PatternMatcher(pattern));
                  }
                } else if (Number.class.isAssignableFrom(fieldType)) {
                   child.setMatcher(new NumberMatcher(fieldType));
                } else {
                  throw new RuntimeException("Unsupported token type: '"+ fieldType +"'");
                }
              }
              trace.addFirst(child);
              continue;
            } else {
              throw new RuntimeException(type + " should implement com.onkiup.linker.parser.Rule interface; ");
            }
          } else {
            testResult = matcher.apply(buffer);
            if (testResult.isMatch()) {
              Object value = testResult.getToken();
              token.finalize(value.toString());
              buffer = new StringBuilder(buffer.substring(testResult.getTokenLength()));
            } else if (testResult.isFailed()){
              trace.removeFirst();

              PartialToken parent;
              boolean pickedAlternative = false;
              while (null != (parent = trace.pollFirst())) {
                if (parent.hasAlternativesLeft()) {
                   parent.advanceAlternative(buffer.toString());
                   trace.addFirst(parent);
                   pickedAlternative = true;
                   break;
                }
              }

              if (pickedAlternative) {
                continue;
              }

              throw new SyntaxError("Expected " +
                  parent +
                " but got <[" + buffer + "]>"
              );
            }
          }
        } 

        nextChar = source.read();
        if (nextChar > 0) {
          buffer.append((char)nextChar);
        } else if (!token.isPopulated() && buffer.length() > 0) {
          if (testResult != null && testResult.isMatchContinue()) {
            Object value = testResult.getToken();
            token.finalize((String)value);
            buffer = new StringBuilder(buffer.substring(testResult.getTokenLength()));
          } 
        }

        // tracing back
        while (token.isPopulated()) {
          trace.removeFirst();
          if (Rule.class.isAssignableFrom(type)) {
            if (isConcrete(type)) {
              token.finalize(context);
            }
          } 
          Object value = token.getToken();

          if (trace.isEmpty()) {
            // SUCESS? 
            return (X) value;
          }
 
          PartialToken<C, ?> parent = (PartialToken<C, ?>) trace.peekFirst();
          Class parentType = parent.getTokenType();
          if (parentType.isArray()) {
            parent.add(value);
          } else if (Rule.class.isAssignableFrom(parentType)) {
            if (isConcrete(parentType)) {
              parent.populateField(value); 
            } else {
              parent.resolve(value);
            }
          }

         token = parent;
         type = token.getTokenType();
        }
      } while(buffer.length() > 0);

      throw new SyntaxError("Unexpected end of input");
    } catch (SyntaxError se) {
      throw se;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

