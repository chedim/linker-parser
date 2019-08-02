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

public class TokenGrammar<X> {
  private Class<X> type;
  private Field[] fields;
  private TokenMatcher[] matchers;
  private static final Reflections reflections  = new Reflections(new ConfigurationBuilder()
        .setUrls(ClasspathHelper.forClassLoader(TokenGrammar.class.getClassLoader()))
        .setScanners(new SubTypesScanner(true))
    );

  private JunctionMatcher<X> interfaceMatcher;

  public static <X extends Rule> TokenGrammar<X> forClass(Class<X> type) {
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
            if (fieldType.isInterface() || Modifier.isAbstract(fieldType.getModifiers())) {
                matchers[i] = prepareJunction(fieldType);
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

  private <Y> JunctionMatcher<Y> prepareJunction(Class<Y> parent) {
    Collection<Class<? extends Y>> variants = reflections.getSubTypesOf(parent).stream()
      .filter(TokenGrammar::isConcrete)
      .collect(Collectors.toList());

    return new JunctionMatcher<Y>(variants, t -> forClass((Class<? extends Rule>) t));
  }

  private <Y> Y recurse(Tokenizer<Y> tokenizer, NestingReader reader, boolean evaluate) {
    AtomicReference<Y> result = new AtomicReference<>();
    reader.nest(source -> {
      Y token = tokenizer.tokenize(source, evaluate);
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
    return parse(source, false);
  }

  public X parse(Reader source, boolean evaluate)  throws SyntaxError {
    X result = read(source, evaluate);
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

  public X read(Reader source, boolean evaluate) throws SyntaxError {
    try {
      PartialToken<X> partial = null;
      if (isConcrete(type)) {
        partial = new PartialToken<X>(type.newInstance());
      } else if (interfaceMatcher == null) {
        throw new IllegalStateException("root matcher is null");
      }
      return read(source, evaluate, partial);
    } catch (IllegalAccessException | InstantiationException e) {
      throw new RuntimeException(e);
    }
  }

  public X read(Reader source, boolean evaluate, PartialToken<X> partial) throws SyntaxError {
    return read(new StringBuilder(), source, evaluate, partial);
  }

  public X read(StringBuilder buffer, Reader source, boolean evaluate, PartialToken<X> partial) throws SyntaxError {
    NestingReader reader = source instanceof NestingReader ? (NestingReader) source : new NestingReader(source);

    if (interfaceMatcher != null) {
      return recurse((Tokenizer<X>) interfaceMatcher, reader, evaluate);
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
              Object token = recurse((Tokenizer<?>) matcher, reader, evaluate);
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
      return partial.getToken();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

