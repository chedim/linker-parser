package com.onkiup.linker.parser;

import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

// at 0.2.2:
// - added "C" type parameter
// - replaced all "evaluate" flags with context
public class TokenGrammar<C, X extends Rule<C>> extends AbstractTokenizer<C, X> {
  private Class<X> type;
  private Field[] fields;
  private TokenMatcher[] matchers;

  private JunctionMatcher<C, X> interfaceMatcher;

  public static <C, X extends Rule<C>> TokenGrammar<C, X> forClass(Class<X> type) {
    return new TokenGrammar<>(type);
  }

  protected TokenGrammar(Class<X> type) {
    this.type = type;
    initialize();
  }

  protected void initialize() {

    if (!isConcrete(type)) { 
      interfaceMatcher = prepareJunction(type);
      return;
    }

    this.fields = type.getDeclaredFields();
    this.matchers = new TokenMatcher[fields.length];
    try {
      X instance = type.newInstance();
      for (int i = 0; i < fields.length; i++) {
        Field field = fields[i];
        try {
          if (field.isAnnotationPresent(CapturePattern.class)) {
            CapturePattern capturePattern = field.getAnnotation(CapturePattern.class);
            matchers[i] = new PatternMatcher(capturePattern);
          } else {
            Class<?> fieldType = field.getType();
            field.setAccessible(true);
            if (fieldType.isArray()) {
              
            } else if (fieldType.isInterface() || Modifier.isAbstract(fieldType.getModifiers())) {
                matchers[i] = prepareJunction((Class<? extends Rule<C>>) fieldType);
            } else {
              if (Number.class.isAssignableFrom(fieldType)) {
                matchers[i] = new NumberMatcher((Class<? extends Number>) fieldType);
              } else if (String.class.isAssignableFrom(fieldType)) {
                Object pattern = field.get(instance);
                if (pattern == null) {
                  throw new RuntimeException("No template provided for terminal '" + field + "'");
                } else {
                  matchers[i] = new TerminalMatcher(pattern.toString());
                }
              }
            }
          }
        } catch (Exception e) {
          throw new RuntimeException("Failed to parse grammar for '" + field + "'", e);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse grammar for '" + type + "'", e);
    }
  }

  private <C, Y extends Rule<C>> JunctionMatcher<C, Y> prepareJunction(Class<Y> parent) {
    Collection<Class<? extends Y>> variants = reflections.getSubTypesOf(parent).stream()
      .filter(TokenGrammar::isConcrete)
      .collect(Collectors.toList());

    return new JunctionMatcher<C, Y>(variants, t -> forClass((Class<Y>) t));
 }

  private <Y extends Rule<C>> Y recurse(Tokenizer<C, Y> tokenizer, NestingReader reader, C context) {
    AtomicReference<Y> result = new AtomicReference<>();
    reader.nest(source -> {
      Y token = tokenizer.tokenize(source, context);
      result.set(token);
      return token != null;
    });

    return result.get();
  }

  public static boolean isConcrete(Class<?> test) {
    return !(test.isInterface() || Modifier.isAbstract(test.getModifiers()));
  }

  public Field[] getFields() {
    return fields;
  }

  public TokenMatcher[] getMatchers() {
    return matchers;
  }

  public Class<X> getTokenType() {
    return type;
  }

  public X parse(Reader source) throws SyntaxError {
    return parse(source, null);
  }

  public X parse(Reader source, C context)  throws SyntaxError {
    X result = read(source, context);
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

  public X read(Reader source, C context) throws SyntaxError {
    try {
      PartialToken<C, X> partial = null;
      if (isConcrete(type)) {
        partial = new PartialToken<C, X>(type.newInstance());
      } else if (interfaceMatcher == null) {
        throw new IllegalStateException("root matcher is null");
      }
      return read(new NestingReader(source), context, partial);
    } catch (IllegalAccessException | InstantiationException e) {
      throw new RuntimeException(e);
    }
  }

  private Set<Class, Matcher> matchers = new HashSet<>();

  public Matcher getMatcher(Field field) {
    Class ofType = field.getType();
    if (Number.class.isAssignableFrom(ofType)) {
      return new NumberMatcher();
    } else if (String.class.isAssignableFrom(ofType)) {
      
    }
    return null;
  }

  public X tokenize(Reader source, C context) throws SyntaxError {
    try {
      Dequeue<PartialToken> trace = new ConcurrentLinkedDequeue<>();
      trace.push(new PartialToken<C, X>(type.newInstance()));
      int nextChar;
      StringBuilder buffer = new StringBuilder();

      do {
        if (buffer.length() > 0) {
          PartialToken token = trace.peekFirst();
          Class type = token.getTokenType();
          Field field = token.getCurrentField();
          TokenTestResult testResult = null;

          if (Rule.class.isAssignableFrom(type)) {
            PartialToken child = new PartialToken(field.getType());
            trace.addFirst(child);
            continue;
          } else if (Number.class.isAssignableFrom(type)){
            Matcher matcher = token.matcher();
            if (matcher == null) {
              matcher = new NumberMatcher((Class<? extends Number>) type);
              token.setMatcher(matcher);
            }
            testResult = matcher.apply(buffer);
          } else if (String.class.isAssignableFrom(type)) {
            Matcher matcher = token.getMatcher();
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
          } else if (!isConcrete()) {
            Class variant = token.getCurrentAlternative();
            trace.addFirst(new PartialToken(variant));
            continue;
          } else if (type.isArray() {
            Class elementType = type.getComponentType();
            trace.addFirst(new PartialToken(elementType));
            continue;
          } else {
            throw new RuntimeException("Unsupported token type: " + type);
          }

          if (testResult.isMatch()) {
            String value = testResult.getToken();
            token.populateField(value);
            buffer = new StringBuilder(buffer.substring(testResult.getTokenLength()));
          } else if (testResult.isFailed()){
            trace.removeFirst();
            PartialToken parent = trace.peekFirst();
            parent.advanceAlternative();
            if (!parent.hasAlternativesLeft()) {
              throw new SyntaxError("Expected " +
                  (field == null) ? type : field) +
                " but got <[" + buffer + "]>";
            }
          }
          
          // tracing back
          while (token.isPopulated()) {
            PartialToken parent = trace.removeFirst();
            if (isConcrete(parent.getTokenType())) {
              // token evaluation happens here
              //
              field = token.getCurrentField();
              Object result = token.getToken(context);

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

  public X read(NestingReader source, C context, PartialToken<C, X> partial) throws SyntaxError {
    if (interfaceMatcher != null) {
      return recurse((Tokenizer<C, X>) interfaceMatcher, reader, context);
    }

    try {
      boolean reachedEof = false;
      for(int i = 0; i < fields.length; i++) {
        Field field = fields[i];
        boolean isOptional = field.isAnnotationPresent(Optional.class);
        TokenMatcher matcher = matchers[i];
        int nextChar = -1;
        TokenTestResult testResult = null;
        do {
          if (buffer.length() > 0) {
            testResult = matcher.apply(buffer);
            if (testResult.isFailed()) {
              if (!isOptional) {
                throw new SyntaxError("Expected <[" + field.getDeclaringClass().getCanonicalName() +
                    "$" + field.getName() + "]> but got <[" + buffer + "]>");
              }
              break;
            } else if (testResult.isRecurse()) {
              reader.pushBack((char)nextChar);
              Object token = recurse((Tokenizer<C, ?>) matcher, reader, context);
              if (token == null && !isOptional) {
                throw new SyntaxError("Expected <[" + field.getType().getCanonicalName() + "]> but got <[" + buffer + "]>");
              }
              partial.populateField(field, token);
              buffer = new StringBuilder();
              break;
            } else if (testResult.isMatch()) {
              partial.populateField(field, testResult.getToken());
              buffer = new StringBuilder(buffer.substring(testResult.getTokenLength()));
              break;
            }
          }
          
          nextChar = reader.read();
          if (!(reachedEof = nextChar < 0)) {
            buffer.append((char)nextChar);
          } else if (testResult != null && testResult.isContinue()) {
            // EOF, but we've got a partial match
            partial.populateField(field, testResult.getToken());
          } else if (buffer.length() > 0) {
            throw new RuntimeException("Trailing uncaptured characters: '" + buffer + "'");
          } else {
            throw new RuntimeException("Unexpected EOF while populating field " + field);
          }
        } while (!reachedEof); 
      }
      return partial.getToken(context);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

