package com.onkiup.linker.parser;

import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// at 0.2.2:
// - added "C" type parameter
// - replaced all "evaluate" flags with context
public class TokenGrammar<C, X extends Rule<C>> {
  private static final Logger logger = LoggerFactory.getLogger(TokenGrammar.class);
  private Class<X> type;

  public static <CC, XX extends Rule<CC>> TokenGrammar<CC, XX> forClass(Class<XX> type) {
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
      ParserState state = new ParserState("", source);
      state.push(new PartialToken<C, X>(type, state.location()));
      TokenTestResult testResult = null;
      boolean hitEnd = false;

      do {
        logger.info("----------------------------------------------------------------------------------------");
        logger.info(state.toString());
        PartialToken<C, ?> token = state.token();
        Class type = token.getTokenType();
        Field field = token.getCurrentField();
        TokenMatcher matcher = token.getMatcher();

        if (!state.empty()) {
          if (matcher == null) {
            if (type.isArray()) {
              logger.info("Populating an array field...");
              state.push(new PartialToken(type.getComponentType(), state.location()));
              continue;
            } if (!isConcrete(type)) {
              logger.info("Trying a junction field...");
              try {
                Class variant = token.getCurrentAlternative();
                state.push(new PartialToken(variant, state.location()));
              } catch (IndexOutOfBoundsException ioobe) {
                logger.info("No variants were available!");
                state.discardAlternative();
              }
              continue;
            } else if (Rule.class.isAssignableFrom(type)) {
              if (field == null) {
                throw new RuntimeException(type + " field is null \n" + state);
              }
              Class fieldType = field.getType();
              PartialToken child;
              if (field.isAnnotationPresent(CustomMatcher.class)) {
                Class<? extends TokenMatcher> matcherType = field.getAnnotation(CustomMatcher.class).value();
                child = new PartialToken(fieldType, state.location());
                try {
                  child.setMatcher(matcherType.newInstance());
                } catch (Exception e) {
                  throw new RuntimeException("Failed to create custom matcher from type " + matcherType, e);
                }
              } else if (!isConcrete(fieldType)) {
                logger.info("Descending to non-concrete field...");
                child = new PartialToken(fieldType, state.location());
              } else if (fieldType.isArray()) {
                logger.info("Descending to populate array field...");
                child = new PartialToken(fieldType, state.location());
              } else {
                logger.info("Creating matcher...");
                child = new PartialToken(field.getType(), state.location());
                if (String.class.isAssignableFrom(fieldType)) {
                  if (Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    String pattern = (String) field.get(null);
                    if (pattern == null) {
                      throw new IllegalArgumentException("Static String fields MUST have a non-null value \n" + state);
                    }
                    child.setMatcher(new TerminalMatcher(pattern));
                  } else if (field.isAnnotationPresent(CapturePattern.class)) {
                    CapturePattern pattern = field.getAnnotation(CapturePattern.class);
                    if (pattern == null) {
                      throw new IllegalArgumentException("Non-static String fields MUST have CapturePattern annotation\n"+state);
                    }
                    child.setMatcher(new PatternMatcher(pattern));
                  }
//                } else if (Number.class.isAssignableFrom(fieldType)) {
//                   child.setMatcher(new NumberMatcher(fieldType));
                } else if (!Rule.class.isAssignableFrom(fieldType)) {
                  throw new RuntimeException("Unsupported token type: '"+ fieldType +"'");
                }
              }
              child.appendIgnoreCharacters(token.getIgnoreCharacters());
              state.push(child);
              continue;
            } else {
              throw new RuntimeException(type + " should implement com.onkiup.linker.parser.Rule interface; ");
            }
          } else { // matcher != null
            testResult = token.test(state.buffer());
            logger.info(testResult.toString());

            if (testResult.isMatch()) {
              Object value = testResult.getToken();
              token.finalize(value.toString());
              String taken = state.drop(testResult.getTokenLength());
              token.appendTaken(taken);
            } else if (testResult.isFailed()){
              state.discardAlternative();
              token = state.token();
              type = token.getTokenType();

              if (!token.isPopulated()) {
                continue;
              }
            }
          }
        } 

        if (!state.advance()) {
          logger.info("!!!!! HIT END !!!!!");
          hitEnd = true; 
          if (!token.isPopulated() && !state.empty() && testResult != null && testResult.isMatchContinue()) {
            logger.info("Force-finalizing MATCH_CONTINUE result");
            Object value = testResult.getToken();
            token.finalize((String)value);
            String taken = state.drop(testResult.getTokenLength());
            token.appendTaken(taken);
          } 
        }

        // tracing back
        while (token.isPopulated() || (state.empty() && !token.hasRequiredFields())) {
          logger.info("Tracing back from " + token + " (depth: " + state.depth() + ")");
          logger.info("Populated: " + token.isPopulated() + "; hit end: " + hitEnd + "; has required fields: " + token.hasRequiredFields() + "; token type: " + type);
          state.pop();
          if (Rule.class.isAssignableFrom(type) || type.isArray()) {
            if (isConcrete(type) || type.isArray()) {
              token.finalize(context);
            }
          } 

          if (state.depth() == 0) {
            // SUCESS? 
            logger.info("Evaluation result: \n" + token);
            return (X) token.getToken();
          }
 
          Object value = token.getToken();
          String taken = token.getTaken().toString();
          PartialToken<C, ?> parent = (PartialToken<C, ?>) state.token();
          Class parentType = parent.getTokenType();
          if (parentType.isArray()) {
            parent.add(value);
            parent.appendTaken(taken);
          } else if (Rule.class.isAssignableFrom(parentType)) {
            parent.appendTaken(taken);
            if (isConcrete(parentType)) {
              parent.populateField(value);
            } else {
              parent.resolve(value);
            }
          }

         token = parent;
         type = token.getTokenType();
        }
        logger.info("Finishing iteration with token " + token + "(populated: " + token.isPopulated() + "; has required fields: " + token.hasRequiredFields() + ")");
        if (hitEnd && state.hasAlternatives() && state.empty()) {
          logger.info("Hit end on unpopulated token...");
          state.discardAlternative();
        }
      } while(!state.empty());

      throw new SyntaxError("Unexpected end of input", state);
    } catch (SyntaxError se) {
      throw se;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

