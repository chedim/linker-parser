package com.onkiup.linker.parser;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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

/**
 * Main class for parsing.
 * Please use {@link #forClass(Class)} to create instances
 * @param <X> type of the object to parse into.
 */
public class TokenGrammar<X extends Rule> {
  private static final Logger logger = LoggerFactory.getLogger("PARSER LOOP");
  private static final ThreadLocal<StringBuilder> BUFFER = new ThreadLocal<>();
  private Class<X> type;
  private Class metaType;
  private String ignoreTrail;

  /**
   * Default constructor
   * @param type resulting token type
   * @return
   */
  public static <XX extends Rule> TokenGrammar<XX> forClass(Class<XX> type) {
    return new TokenGrammar<>(type, null);
  }
  
  /**
   * For future handling of metatokens like comments
   * @param type resulting token type
   * @param metaType meta token type
   * @return
   */
  public static <XX extends Rule> TokenGrammar<XX> forClass(Class<XX> type, Class metaType) {
    return new TokenGrammar<>(type, metaType);
  }

  protected TokenGrammar(Class<X> type, Class metaType) {
    this.type = type;
    this.metaType = metaType;
  }

  /**
   * @param test Type to test
   * @return true if the type is not abstract
   */
  public static boolean isConcrete(Class<?> test) {
    return !(test.isInterface() || Modifier.isAbstract(test.getModifiers()));
  }

  /**
   * @return resulting token type
   */
  public Class<X> getTokenType() {
    return type;
  }

  /**
   * Configures this parser to ignore trailing characters based on the input string
   * @param chars trailing characters to ignore
   */
  public void ignoreTrailCharacters(String chars) {
    this.ignoreTrail = chars;
  }

  /**
   * Parses a string into resulting token
   * @param source string to parse
   * @return parsed token
   * @throws SyntaxError
   */
  public X parse(String source) throws SyntaxError {
    return parse("unknown", source);
  }

  /**
   * Parses named string to resulting token
   * @param name name of the source that will be parsed
   * @param source contents to parse
   * @return parsed token
   * @throws SyntaxError
   */
  public X parse(String name, String source) throws SyntaxError {
    return parse(name, new StringReader(source));
  }

  /**
   * Parses contents from a Reader
   * @param source Reader to get contents from
   * @return parsed token
   * @throws SyntaxError
   */
  public X parse(Reader source) throws SyntaxError {
    return parse("unknown", source);
  }

  /**
   * Parses named text from a Reader
   * @param name name of the source
   * @param source reader to get contents from
   * @return parsed token
   * @throws SyntaxError
   */
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

  /**
   * Parses contents from the reader
   * @param source reader to get contents from
   * @return parsed token
   * @throws SyntaxError
   */
  public X tokenize(Reader source) throws SyntaxError {
    return tokenize("unknown", source);
  }

  /**
   * Main parser entrance
   * @param sourceName the name of the source that will be parsed
   * @param source reader to get contents from
   * @return parsed token
   * @throws SyntaxError
   */
  public X tokenize(String sourceName, Reader source) throws SyntaxError {
    AtomicInteger position = new AtomicInteger(0);
    ParserContext.get().classLoader(getTokenType().getClassLoader());
    SelfPopulatingBuffer buffer = null;
    try {
      buffer = new SelfPopulatingBuffer(sourceName, source);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read source " + sourceName, e);
    }
    try {
      CompoundToken<X> rootToken = CompoundToken.forClass(type, 0, new ParserLocation(sourceName, 0, 0, 0));
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

  /**
   * Tries to recover from a situation where parser populates AST before the whole source is processed by either
   * validating all trailing characters, rotating root token, or tracing back to the next umtested grammar junction
   * @param rootToken the root token of failing AST
   * @param buffer a reference to a buffer with source contents
   * @param position position in the buffer at which early population occured
   * @return empty optional if failed to recover, or next consuming token after successfull recovery
   */
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

  /**
   * Propagates population event from child token to its parents until parent tokens report they are populated
   * @param child populated token
   * @param hitEnd a flag that indicates that parent tokens should not expect any future characters to be consume and should be either populated or failed after receiving this event (not unfailed and unpopulated)
   * @return next consuming token from the AST or empty when all parents are populated of one of the parents reported to fail and there is no alternatives left
   */
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

  /**
   * Traces back from a failed token to its first parent with left alternatives, then advances to the next available alternative
   * @param child failed token
   * @return consuming token from the next available alternative or empty
   */
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

  /**
   * traces back to a first unpopulated parent
   * @param child token to trace back from
   * @return the first unpopulated parent
   */
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

  /**
   * Advances to the next available consuming token after passed token; traces back any failed tokens it finds while advancing
   * @param from token to advance from
   * @return next consuming token
   */
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

  /**
   * Advances to the next available consuming token in the parent of provided consuming token
   * @see #nextConsumingToken(CompoundToken)
   * @param from consuming token to advance from
   * @return next consuming token in the AST
   */
  private static Optional<ConsumingToken<?>> nextConsumingToken(ConsumingToken<?> from) {
    return from.parent().flatMap(TokenGrammar::nextConsumingToken);
  }

  /**
   * Continuously calls ConsumingToken::consume until the method returns false and then adjusts parser position to
   * the end of the token
   * @param token token that should consume characters from parser's buffer
   * @param position parser position to update with consuming token's end position after the consumption is complete
   */
  private void processConsumingToken(ConsumingToken<?> token, AtomicInteger position) {
    while (token.consume()) {
      //position.incrementAndGet();
    }
    position.set(token.end().position());
  }

  /**
   * Validates all characters in provided buffer starting with provided position to be in preconfigured ignored trailing characters list
   * @param buffer buffer to validate
   * @param from starting position
   * @return true if all characters starting from provided position can be ighored, false otherwise
   */
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

  /**
   * Configures log4j appenders with custom {@link LoggerLayout}
   * @param buffer parser buffer to display in logs
   * @param position supplier of current parser position to display in logs
   */
  private void setupLoggingLayouts(CharSequence buffer, Supplier<Integer> position) {
    Enumeration<Appender> appenders = org.apache.log4j.Logger.getRootLogger().getAllAppenders();
    while(appenders.hasMoreElements()) {
      Appender appender = appenders.nextElement();
      LoggerLayout loggerLayout = new LoggerLayout(appender.getLayout(), buffer, position);
      appender.setLayout(loggerLayout);
    }
  }

  /**
   * Removes custom {@link LoggerLayout} configurations from log4j appenders
   */
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

  public static <X extends Rule> boolean isRule(Class<? extends X> aClass) {
    return Arrays.stream(aClass.getInterfaces())
        .anyMatch(Rule.class::equals);
  }
}

