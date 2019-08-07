package com.onkiup.linker.parser;

public class AbstractTokenizer<C, X> implements Tokenizer<C, X>, TokenMatcher<X> {

  protected abstract Set<Class<? extends X>> getVariants();
  protected abstract <Y extends X> TokenGrammar<C, Y> getGrammar(Class<Y> type);


  @Override
  public X tokenize(NestingReader source, C context) {
    Set<Class<? extends X>> variantTypes = getVariants();
 
    Map<Class<? extends X>, TokenTestResult> prevMatches = null;
    Map<Class<? extends X>, StringBuilder> buffers = new HashMap<>();
    int nextChar;
    final StringBuffer spilloverBuffer = new StringBuffer();
    final AtomicInteger spilloverOffset = new AtomicInteger();
    Supplier<Integer> charSupplier = () -> {
      synchronized (spilloverBuffer) {
        if (spilloverOffset.get() < spilloverBuffer.length()) {
          return (int) spilloverBuffer.charAt(spilloverOffset.getAndIncrement());
        } else {
          try {
            return source.read();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      }
    };


    try {
      while (-1 != (nextChar = charSupplier.get())) {
        Set<Class<? extends X>> failed = new HashSet<>();
        Map<Class<? extends X>, TokenTestResult> lastMatches = new HashMap<>();
        boolean contin = false;
        for(Class<? extends X> variantType : variantTypes) {
          if (!buffers.containsKey(variantType)) {
            buffers.put(variantType, new StringBuilder());
          }
          StringBuilder buffer = buffers.get(variantType);
          buffer.append((char)nextChar);

          PartialToken partial = variants.get(variantType);

          TokenGrammar<C, ? extends X> grammar = (TokenGrammar<C, ? extends X>) grammarProvider.apply(variantType);
          Field nextField = grammar.getFields()[partial.getPopulatedFieldCount()];
          TokenMatcher nextMatcher = grammar.getMatchers()[partial.getPopulatedFieldCount()];

          TokenTestResult testResult = nextMatcher.apply(buffer);
          if (testResult.getResult() == TestResult.RECURSE) {
            // This branch implements "Lookahead" feature
            if (!(nextMatcher instanceof Tokenizer)) {
              throw new IllegalStateException("Matcher must implement Tokenizer interface to support recursive tokenizing");
            }
            Tokenizer tokenizer = (Tokenizer) nextMatcher;
            source.nest(reader -> {
              Object result = tokenizer.tokenize(reader, context);
              if (result != null) {
                partial.populateField(nextField, result);
                return true;
              }
              return false;
            });
          } else if (testResult.getResult() == TestResult.FAIL) {
            failed.add(variantType);
          } else if (testResult.getResult() == TestResult.MATCH) {
            // field match!!
            partial.populateField(nextField, testResult.getToken());
            String spillover = buffer.substring(testResult.getTokenLength());
            if (spillover.length() > 0) {
              synchronized(spilloverBuffer) {
                spilloverBuffer.replace(0, spilloverOffset.get(), spillover);
                spilloverOffset.set(0);
              }
            }
            lastMatches.put(variantType, testResult);
          } else {
            contin = contin || testResult.getResult() == TestResult.MATCH_CONTINUE;
            lastMatches.put(variantType, testResult);
          }
        }

        if (lastMatches.size() == 1) {
          Class<? extends X> winnerType = lastMatches.keySet().iterator().next();
          TokenGrammar<C, ? extends X> grammar = (TokenGrammar<C, ? extends X>) grammarProvider.apply(winnerType);
          StringBuilder buffer = buffers.get(winnerType);
          return (X) grammar.read(buffer, source, context, variants.get(winnerType));
        }

        variantTypes.removeAll(failed);

        prevMatches = lastMatches;
      }
    } catch(Exception e) {
      throw new RuntimeException("Failed to tokenize", e);
    }

    return null;
  }

  @Override
  public TokenTestResult apply(StringBuilder buffer) {
    return TestResult.recurse();
  }
}

