package com.onkiup.linker.parser;

import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onkiup.linker.parser.token.ConsumingToken;
import com.onkiup.linker.parser.token.FailedToken;
import com.onkiup.linker.parser.token.PartialToken;

// at 0.2.2:
// - replaced all "evaluate" flags with context
public class TokenGrammar<X extends Rule> {
  private static final Logger logger = LoggerFactory.getLogger(TokenGrammar.class);
  private Class<X> type;

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

  public X parse(String source) throws SyntaxError {
    return parse(new StringReader(source));
  }

  public X parse(Reader source) throws SyntaxError {
    return parse(source, null);
  }

  public X parse(Reader source, Object context)  throws SyntaxError {
    X result = tokenize(source);
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
    return tokenize("", source);
  }

  public X tokenize(String sourceName, Reader source) throws SyntaxError {
    PartialToken<X> rootToken = PartialToken.forClass(null, type, 0);
    PartialToken token = rootToken;
    PartialToken previousToken = token;
    StringBuilder buffer = new StringBuilder();
    boolean hitEnd = false;
    int position = 0, line = 0, col = 0;
    try {
      do {
        logger.debug("----------------------------------------------------------------------------------------");
        if (logger.isDebugEnabled()) {
          Collection<PartialToken> path = token.getPath();

          String trace = (String) path.stream()
            .map(Object::toString)
            .collect(Collectors.joining("\n"));
          logger.debug("Trace:\n{}", trace);
        }

        while (buffer.length() < 2 && !hitEnd) {
          int nextChar = source.read();
          hitEnd = nextChar < 0;
          if (!hitEnd) {
            buffer.append((char) nextChar);

            position++;
            col++;

            if (nextChar == '\n') {
              line++;
              col = 0;
            }
          }
        }

        if (buffer.length() > 0 && token instanceof ConsumingToken) {
          logger.debug("Feeding character '{}' to {}", buffer.charAt(0), token);
          final ConsumingToken consumingToken = (ConsumingToken) token;
          boolean consumed = (Boolean)consumingToken.consume(buffer.charAt(0), buffer.length() == 1)
            .map(Object::toString)
            .map(returned -> {
              logger.debug("Token {} returned characters: {}", consumingToken, returned);
              buffer.replace(0, 1, (String) returned);
              return false;
            })
            .orElseGet(() -> {
              logger.debug("Discarding consumed by token {} character '{}'", consumingToken, buffer.charAt(0));
              buffer.delete(0, 1);
              return true;
            });

          if (consumed) {
            // prevents parser from advancing to next token
            continue;
          }
        }
        
        previousToken = token;

        do {
          token = (PartialToken) token.advance(buffer.length() == 0).orElse(null);
          logger.debug("Advanced to token {}", token);
          if (token instanceof FailedToken) {
            String returned = (String) token.getToken();
            logger.info("Received from failed token: '{}'", returned);
            buffer.append(returned);
          }
        } while (token instanceof FailedToken);


        if (token == null) {
          if (buffer.length() > 0 || !hitEnd) {
            logger.debug("Trying to rotate root token to avoid unmatched characters...");
            if (rootToken.rotatable()) {
              rootToken.rotate();
              logger.debug("Rotated root token");
              token = rootToken;
              continue;
            }

            int alternativesLeft = rootToken.alternativesLeft();
            logger.debug("Alternatives left for {}: {}", rootToken, alternativesLeft);
            if (alternativesLeft > 0) {
              logger.debug("Hit end but root token had alternatives left; backtracking the token and continuing with next variant");
              rootToken.pullback().ifPresent(b -> buffer.insert(0, b));
              token = rootToken;
              continue;
            }

            int nextChar;
            while (-1 != (nextChar = source.read())) {
              buffer.append((char)nextChar);
            }
            throw new SyntaxError("Unmatched trailing characters", previousToken, buffer);
          } else {
            rootToken.sortPriorities();
            return rootToken.getToken();
          }
        }
      } while(buffer.length() > 0);

      throw new SyntaxError("Unexpected end of input", previousToken, buffer);
    } catch (SyntaxError se) {
      throw new RuntimeException("Syntax error at line " + line + ", column " + col, se);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}

