package com.onkiup.linker.parser;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
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
import com.onkiup.linker.parser.util.SelfPopulatingBuffer;

import sun.rmi.runtime.Log;

// at 0.2.2:
// - replaced all "evaluate" flags with context
public class TokenGrammar<X extends Rule> {
  private static final Logger logger = LoggerFactory.getLogger("PARSER LOOP");
  private static final ThreadLocal<StringBuilder> BUFFER = new ThreadLocal<>();
  private Class<X> type;
  private Class metaType;
  private String ignoreTrail;

  public static <XX extends Rule> TokenGrammar<XX> forClass(Class<XX> type) {
    return new TokenGrammar<>(type, null);
  }

  public static <XX extends Rule> TokenGrammar<XX> forClass(Class<XX> type, Class metaType) {
    return new TokenGrammar<>(type, metaType);
  }

  protected TokenGrammar(Class<X> type, Class metaType) {
    this.type = type;
    this.metaType = metaType;
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
    AtomicInteger position = new AtomicInteger(0);
    SelfPopulatingBuffer buffer = null;
    try {
      buffer = new SelfPopulatingBuffer(sourceName, source);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read source " + sourceName, e);
    }
    try {
      CompoundToken<X> rootToken = CompoundToken.forClass(type, new ParserLocation(sourceName, 0, 0, 0));
      ConsumingToken.ConsumptionState.rootBuffer(rootToken, buffer);
      CompoundToken parent = rootToken;
      ConsumingToken<?> consumer = nextConsumingToken(parent).orElseThrow(() -> new ParserError("No possible consuming tokens found", parent));
      ConsumingToken<?> bestFail = consumer;
      setupLoggingLayouts(buffer, position::get);
      do {
        if (logger.isDebugEnabled()) {
          System.out.print("\u001B[H\u001Bc");
          System.out.println("|----------------------------------------------------------------------------------------");
          System.out.println(consumer.location().toString());
          System.out.println("|----------------------------------------------------------------------------------------");
          final ConsumingToken<?> currentConsumer = consumer;
          System.out.print(rootToken.dumpTree(token -> {
            StringBuilder result = new StringBuilder();
            if (token == currentConsumer) {
              result.append(">>> ");
            }
            return result
                .append(token.getClass().getSimpleName())
                .append("(").append(token.position()).append(" - ").append(token.end().position()).append(")")
                .append(" :: '")
                .append(LoggerLayout.sanitize(token.head(50)))
                .append("'");
          }));
          System.out.println("|----------------------------------------------------------------------------------------");
          System.out.println("|----------------------------------------------------------------------------------------");
        }

        ConsumingToken lastConsumer = consumer;

        processConsumingToken(consumer, position);
        boolean hitEnd = position.get() >= buffer.length();

        if (consumer.isFailed()) {
          logger.debug("!!! CONSUMER FAILED !!! {}", consumer.tag());
          bestFail = bestFail.position() > consumer.position() ? bestFail : consumer;
          consumer = processTraceback(consumer).orElse(null);
        } else if (consumer.isPopulated()) {
          logger.debug("consumer populated: {}", consumer.tag());
          consumer = onPopulated(consumer, hitEnd).orElse(null);
        } else if (hitEnd) {
          logger.debug("Hit end while processing {}", consumer.tag());
          consumer.atEnd();
          consumer = nextConsumingToken(consumer).orElse(null);
        }

        if (consumer != null) {
          position.set(consumer.end().position());
        }

        if (consumer == null || hitEnd) {
          logger.debug("attempting to recover; consumer == {}, buffer.length() == {}", consumer == null ? null : consumer.tag(), buffer.length());
          if (rootToken.isPopulated()) {
            if (!hitEnd) {
              if (!validateTrailingCharacters(buffer, position.get())) {
                consumer = processEarlyPopulation(rootToken, buffer, position.get()).orElseThrow(
                    () -> new ParserError("Failed to recover from early population", lastConsumer));
                logger.debug("Recovered to {}", consumer.tag());
              } else {
                logger.debug("Successfully parsed (with valid trailing characters '{}') into: {}", buffer.subSequence(position.get(), buffer.length()), rootToken.tag());
                return rootToken.token().orElse(null);
              }
            } else {
              logger.debug("Perfectly parsed into: {}", rootToken.tag());
              return rootToken.token().get();
            }
          } else if (consumer != null) {
            logger.debug("Hit end and root token is not populated -- trying to traceback...");
            do {
              consumer.onFail();
              consumer = processTraceback(consumer).orElse(null);
            } while (buffer.length() == 0 && consumer != null);

            if (consumer != null && rootToken.isPopulated()) {
              consumer = processEarlyPopulation(rootToken, buffer, position.get()).orElseThrow(() ->
                  new ParserError("Failed to recover from null consumer", lastConsumer));
              logger.debug("Recovered to {}", consumer.tag());
            } else if (rootToken.isPopulated()) {
              return rootToken.token().get();
            }
          } else {
            throw new SyntaxError("Advanced up to this token and then failed", bestFail, buffer);
          }
        }

      } while(consumer != null && position.get() < buffer.length());

      if (rootToken.isPopulated()) {
        return rootToken.token().orElse(null);
      }

      throw new SyntaxError("Unexpected end of input", consumer, buffer);
    } catch (SyntaxError se) {
      throw new RuntimeException("Syntax error at position " + position.get(), se);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      restoreLoggingLayouts();
    }
  }

  private Optional<ConsumingToken<?>> processEarlyPopulation(CompoundToken<?> rootToken, CharSequence buffer, int position) {
    logger.debug("Early population detected...");
    if (validateTrailingCharacters(buffer, position)) {
      logger.debug("Successfully parsed (with valid trailing characters '{}') into: {}", buffer, rootToken.tag());
      return Optional.empty();
    } else if (rootToken.rotatable()) {
      logger.debug("Rotating root token");
      rootToken.rotate();
      return nextConsumingToken(rootToken);
    } else if (rootToken.alternativesLeft()) {
      logger.info("Root token populated too early, failing it... (Buffer left: '{}'", LoggerLayout.sanitize(buffer.subSequence(position, buffer.length())));
      rootToken.traceback();
      return nextConsumingToken(rootToken);
    } else {
      return Optional.empty();
    }
  }

  private static Optional<ConsumingToken<?>> onPopulated(PartialToken<?> child, boolean hitEnd) {
    return child.parent().flatMap(parent -> {
      parent.onChildPopulated();
      if (hitEnd) {
        parent.atEnd();
      }
      if (parent.isPopulated()) {
        return onPopulated(parent, hitEnd);
      } else if (parent.isFailed()) {
        return processTraceback(parent);
      }
      return nextConsumingToken(parent);
    });
  }

  private static Optional<ConsumingToken<?>> processTraceback(PartialToken<?> child) {
    return child.parent().flatMap(parent -> {
      if (child.isFailed()) {
        logger.debug("^^^--- TRACEBACK: {} <- {}", parent.tag(), child.tag());
        parent.onChildFailed();
        if (parent.isFailed() || parent.isPopulated()) {
          if (parent.isPopulated()) {
            return onPopulated(parent, false);
          }
          return processTraceback(parent);
        }

        if (!child.isOptional()) {
          parent.traceback();
        } else {
          child.traceback();
        }
        return firstUnfilledParent(parent).flatMap(TokenGrammar::nextConsumingToken);
      } else {
        logger.debug("|||--- TRACEBACK: (self) <- {}", child.tag());
        return firstUnfilledParent(child).flatMap(TokenGrammar::nextConsumingToken);
      }
    });
  }

  private static Optional<CompoundToken<?>> firstUnfilledParent(PartialToken<?> child) {
    logger.debug("traversing back to first unfilled parent from {}", child.tag());
    if (child instanceof CompoundToken && !child.isFailed() && ((CompoundToken<?>)child).unfilledChildren() > 0) {
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
      logger.debug("Searching for next consumer in child {}", child == null ? null : child.tag());
      if (child instanceof ConsumingToken) {
        logger.debug("--->>> NEXT CONSUMER: {} ---> {}", from.tag(), child.tag());
        return Optional.of((ConsumingToken<?>)child);
      } else if (child instanceof CompoundToken) {
        logger.debug("--->>> searching for next consumer in {} --> {}", from.tag(), child.tag());
        from = (CompoundToken)child;
      } else if (child == null) {
        CompoundToken<?> parent = from.parent().orElse(null);
        logger.debug("^^^--- searching for next consumer in parent {} <--- {}", parent == null ? null : parent.tag(), from.tag());
        if (from.isFailed()) {
          logger.debug("notifying parent about child failure");
          return processTraceback(from);
        } else if (from.isPopulated()) {
          logger.debug("notifying parent about child population");
          return onPopulated(from, false);
        } else {
          throw new ParserError("next child == null but from is neither failed or populated", from);
        }
      } else {
        throw new RuntimeException("Unknown child type: " + child.getClass());
      }
    }
    logger.debug("---XXX NEXT CONSUMER: {} ---> XXX (not found)", from == null ? null : from.tag());
    return Optional.empty();
  }

  private static Optional<ConsumingToken<?>> nextConsumingToken(ConsumingToken<?> from) {
    return from.parent().flatMap(TokenGrammar::nextConsumingToken);
  }

  private void processConsumingToken(ConsumingToken<?> token, AtomicInteger position) {
    while (token.consume()) {
      //position.incrementAndGet();
    }
    position.set(token.end().position());
  }

  private boolean validateTrailingCharacters(CharSequence buffer, int from) {
    logger.debug("Validating trailing characters with pattern '{}' on '{}'", LoggerLayout.sanitize(ignoreTrail), LoggerLayout.sanitize(buffer.subSequence(from, buffer.length())));
    if (from >= buffer.length()) {
      logger.debug("no trailing chars!");
      return true;
    }
    char character;
    do {
      character = buffer.charAt(from++);
    } while (buffer.length() > from && ignoreTrail != null && ignoreTrail.indexOf(character) > -1);
    boolean result = from >= buffer.length();
    logger.debug("Only valid trailing chars left? {}; from == {}; buffer.length == {}", result, from, buffer.length());
    return result;
  }

  private void setupLoggingLayouts(CharSequence buffer, Supplier<Integer> position) {
    Enumeration<Appender> appenders = org.apache.log4j.Logger.getRootLogger().getAllAppenders();
    while(appenders.hasMoreElements()) {
      Appender appender = appenders.nextElement();
      LoggerLayout loggerLayout = new LoggerLayout(appender.getLayout(), buffer, position);
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

