package com.onkiup.linker.parser;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.log4j.Appender;
import org.apache.log4j.Layout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onkiup.linker.parser.token.CompoundToken;
import com.onkiup.linker.parser.token.ConsumingToken;
import com.onkiup.linker.parser.token.PartialToken;
import com.onkiup.linker.parser.util.LoggerLayout;
import com.onkiup.linker.parser.util.ParserError;

// at 0.2.2:
// - replaced all "evaluate" flags with context
public class TokenGrammar<X extends Rule> {
  private static final Logger logger = LoggerFactory.getLogger("PARSER LOOP");
  private static final ThreadLocal<StringBuilder> BUFFER = new ThreadLocal<>();
  private Class<X> type;
  private String ignoreTrail;

  public static <XX extends Rule> TokenGrammar<XX> forClass(Class<XX> type) {
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

  public void ignoreTrailCharacters(String chars) {
    this.ignoreTrail = chars;
  }

  public X parse(String source) throws SyntaxError {
    return parse("unknown", source);
  }
  public X parse(String name, String source) throws SyntaxError {
    return parse(name, new StringReader(source));
  }

  public X parse(Reader source) throws SyntaxError {
    return parse("unknown", source);
  }

  public X parse(String name, Reader source)  throws SyntaxError {
    X result = tokenize(name, source);
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
      throw new SyntaxError("Unmatched trailing symbols: '" + tail + "'", null, tail);
    }
    return result;
  }

  public X tokenize(Reader source) throws SyntaxError {
    return tokenize("unknown", source);
  }

  public X tokenize(String sourceName, Reader source) throws SyntaxError {
    CompoundToken<X> rootToken = CompoundToken.forClass(type, new ParserLocation(sourceName, 0, 0, 0));
    CompoundToken parent = rootToken;
    ConsumingToken<?> consumer = nextConsumingToken(parent).orElseThrow(() -> new ParserError("No possible consuming tokens found", parent));
    StringBuilder buffer = new StringBuilder();
    int line = 0, col = 0;
    AtomicInteger position = new AtomicInteger(0);
    try {
      setupLoggingLayouts(buffer);
      do {
        if (logger.isDebugEnabled()) {
          System.out.println("|----------------------------------------------------------------------------------------");
          System.out.println("|----------------------------------------------------------------------------------------");
          System.out.println("|----------------------------------------------------------------------------------------");
          Collection<PartialToken> path = consumer.path();

          String trace = path.stream()
            .map(Object::toString)
            .collect(Collectors.joining("\n|-"));
          System.out.println("|-" + trace);
          System.out.println("|----------------------------------------------------------------------------------------");
          System.out.println("|----------------------------------------------------------------------------------------");
        }

        ConsumingToken lastConsumer = consumer;

        try {
          boolean hitEnd = processConsumingToken(source, buffer, consumer);
          if (hitEnd) {
            logger.debug("Hit end while processing {}", consumer.tag());
            if (!consumer.isPopulated() && !consumer.isFailed()) {
              consumer.onFail();
            }
          }

          if (consumer.isFailed()) {
            logger.debug("!!! CONSUMER FAILED !!! {}", consumer.tag());
            //if (!consumer.isOptional()) {
              consumer = processTraceback(consumer, buffer).orElse(null);
            //}
          } else if (consumer.isPopulated()) {
            logger.debug("consumer populated: {}", consumer.tag());
            consumer = onPopulated(consumer)
                .flatMap(TokenGrammar::nextConsumingToken)
                .orElse(null);
          }

          if (consumer == lastConsumer) {
            consumer = nextConsumingToken(consumer).orElse(null);
          }

          if (consumer == null || buffer.length() == 0) {
            if (rootToken.isPopulated()) {
              if (!hitEnd) {
                 consumer = processEarlyPopulation(rootToken, source, buffer).orElse(null);
              } else {
                logger.debug("Perfectly parsed into: {}", rootToken.tag());
                return rootToken.token().get();
              }
            } else if (consumer != null) {
              logger.debug("Hit end and root token is not populated -- trying to traceback...");
              do {
                consumer.onFail();
                consumer = processTraceback(consumer, buffer).orElse(null);
              } while (buffer.length() == 0 && consumer != null);

              if (buffer.length() != 0 && rootToken.isPopulated()) {
                consumer = processEarlyPopulation(rootToken, source, buffer).orElse(null);
              } else if (rootToken.isPopulated()) {
                return rootToken.token().get();
              }
            }
          }
        } catch (IOException ioe) {
          throw new RuntimeException("Failed to read source data", ioe);
        }

      } while(consumer != null && buffer.length() > 0);

      if (rootToken.isPopulated()) {
        return rootToken.token().orElse(null);
      }

      throw new SyntaxError("Unexpected end of input", rootToken, buffer);
    } catch (SyntaxError se) {
      throw new RuntimeException("Syntax error at line " + line + ", column " + col, se);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      restoreLoggingLayouts();
    }
  }

  private Optional<ConsumingToken<?>> processEarlyPopulation(CompoundToken<?> rootToken, Reader source, StringBuilder buffer) throws IOException {
    if (validateTrailingCharacters(source, buffer)) {
      logger.debug("Successfully parsed (with valid trailing characters '{}') into: {}", buffer, rootToken.tag());
      buffer.delete(0, buffer.length());
      return Optional.empty();
    } else if (rootToken.rotatable()) {
      rootToken.rotate();
      return nextConsumingToken(rootToken);
    } else if (rootToken.alternativesLeft() > 0) {
      logger.info("Root token populated too early, failing it...");
      rootToken.traceback().ifPresent(returned -> {
        logger.debug("Returned by root token: '{}'", LoggerLayout.sanitize(returned));
        buffer.insert(0, returned);
      });
      rootToken.onFail();
      return nextConsumingToken(rootToken);
    } else {
      return Optional.empty();
    }
  }

  private static Optional<CompoundToken<?>> onPopulated(PartialToken<?> child) {
    return child.parent().flatMap(parent -> {
      parent.onChildPopulated();
      if (parent.isPopulated()) {
        return onPopulated(parent);
      }
      return Optional.of(parent);
    });
  }

  private static Optional<ConsumingToken<?>> processTraceback(PartialToken<?> child, StringBuilder buffer) {
    return child.parent().flatMap(parent -> {
      if (child.isFailed()) {
        logger.debug("^^^--- TRACEBACK: {} <- {}", parent.tag(), child.tag());
        parent.onChildFailed();
        if (parent.isFailed() || parent.isPopulated()) {
          return processTraceback(parent, buffer);
        }

        parent.traceback().ifPresent(returned -> buffer.insert(0, returned.toString()));
        return nextConsumingToken(parent);
      } else {
        logger.debug("|||--- TRACEBACK: (self) <- {}", child.tag());
        return firstUnfilledParent(child).flatMap(TokenGrammar::nextConsumingToken);
      }
    });
  }

  private static Optional<CompoundToken<?>> firstUnfilledParent(PartialToken<?> child) {
    logger.debug("traversing back to first unfilled parent from {}", child.tag());
    if (child instanceof CompoundToken && ((CompoundToken<?>)child).unfilledChildren() > 0) {
      logger.debug("<<<--- NEXT UNFILLED: (self) <--- {}", child.tag());
      return Optional.of((CompoundToken<?>)child);
    }

    return Optional.ofNullable(
        child.parent().flatMap(parent -> {
          logger.debug("parent: {}", parent.tag());
          parent.onChildPopulated();
          if (parent.isPopulated()) {
            logger.debug("^^^--- NEXT UNFILLED: {} <-?- {}", parent.tag(), child.tag());
            return firstUnfilledParent(parent);
          } else {
            logger.debug("<<<--- NEXT UNFILLED: {} <--- {}", parent.tag(), child.tag());
            return Optional.of(parent);
          }
        }).orElseGet(() -> {
          if (child instanceof CompoundToken) {
            logger.debug("XXX NO NEXT UNFILLED: XXX <--- {} (compound: true, unfilled children: {}", child, ((CompoundToken<?>)child).unfilledChildren());
          } else {
            logger.debug("XXX NO NEXT UNFILLED: XXX <--- {}", child);
          }
          return null;
        })
    );
  }
  
  public static Optional<ConsumingToken<?>> nextConsumingToken(CompoundToken<?> from) {
    while (from != null) {
      PartialToken<?> child = from.nextChild().orElse(null);
      if (child instanceof ConsumingToken) {
        logger.debug("--->>> NEXT CONSUMER: {} ---> {}", from.tag(), child.tag());
        return Optional.of((ConsumingToken<?>)child);
      } else if (child instanceof CompoundToken) {
          from = (CompoundToken)child;
      } else if (child == null) {
        from = from.parent().orElse(null);
      } else {
        throw new RuntimeException("Unknown child type: " + child.getClass());
      }
    }
    logger.debug("---XXX NEXT UNFILLED: {} ---> XXX (not found)", from);
    return Optional.empty();
  }

  private static Optional<ConsumingToken<?>> nextConsumingToken(ConsumingToken<?> from) {
    return from.parent().flatMap(TokenGrammar::nextConsumingToken);
  }

  private boolean processConsumingToken(Reader source, StringBuilder buffer, ConsumingToken<?> token) throws IOException {
    boolean accepted = true;
    while (accepted) {
      if (populateBuffer(source, buffer)) {
        char character = buffer.charAt(0);
        accepted = token.consume(buffer.charAt(0)).map(CharSequence::toString).map(returned -> {
          logger.debug("------ RETURN: '{}' +++ {} = '{}'", LoggerLayout.sanitize(String.valueOf(character)), token.tag(), LoggerLayout.sanitize(returned));
          buffer.replace(0, 1, returned);
          return false;
        }).orElseGet(() -> {
          logger.debug("---+++ CONSUME: '{}' +++ {}", LoggerLayout.sanitize(String.valueOf(character)), token.tag());
          buffer.delete(0, 1);
          return true;
        });
      } else {
        return true;
      }
    }
    return !populateBuffer(source, buffer);
  }

  private boolean populateBuffer(Reader source, StringBuilder buffer) throws IOException {
    if (buffer.length() == 0) {
      int character = source.read();
      if (character < 0) {
        return false;
      } else {
        buffer.append((char)character);
      }
    }
    return true;
  }

  private boolean validateTrailingCharacters(Reader source, StringBuilder buffer) throws IOException {
    boolean hitEnd = false;
    do {
      populateBuffer(source, buffer);
      if (buffer.length() > 0) {
        char test = buffer.charAt(0);
        if (ignoreTrail != null && ignoreTrail.indexOf(test) < 0) {
          return false;
        }
        buffer.delete(0, 1);
        populateBuffer(source, buffer);
      }
    } while (buffer.length() > 0);
    return true;
  }

  private void setupLoggingLayouts(StringBuilder buffer) {
    Enumeration<Appender> appenders = org.apache.log4j.Logger.getRootLogger().getAllAppenders();
    while(appenders.hasMoreElements()) {
      Appender appender = appenders.nextElement();
      LoggerLayout loggerLayout = new LoggerLayout(appender.getLayout(), buffer);
      appender.setLayout(loggerLayout);
    }
  }

  private void restoreLoggingLayouts() {
    Enumeration<Appender> appenders = org.apache.log4j.Logger.getRootLogger().getAllAppenders();
    while(appenders.hasMoreElements()) {
      Appender appender = appenders.nextElement();
      Layout layout = appender.getLayout();
      if (layout instanceof LoggerLayout) {
        LoggerLayout loggerLayout = (LoggerLayout) layout;
        appender.setLayout(loggerLayout.parent());
      }
    }
  }
}

