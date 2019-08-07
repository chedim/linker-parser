package com.onkiup.linker.parser;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// in 0.2.2:
// - Added  C type parameter
// - bound X to Rule
public class JunctionMatcher<C, X extends Rule<C>> implements TokenMatcher, Tokenizer<C, X> {
  private Map<Class<?>, TokenGrammar<C, ?>> grammarCache = new HashMap<>();

  private Map<Class<? extends X>, PartialToken> variants;
  private Function<Class<? extends Rule<C>>, TokenGrammar<C, ?>> grammarProvider;

  public JunctionMatcher(Collection<Class<? extends X>> variants, Function<Class<? extends Rule<C>>, TokenGrammar<C, ? extends X>> grammarProvider) {
    this.variants = variants.stream().collect(Collectors.toMap(v->v, v -> {
      try {
        return new PartialToken(v.newInstance());
      } catch (Exception e) {
        throw new RuntimeException("Failed to create token " + v);
      }
    }));

    this.grammarProvider = type -> {
       if(!grammarCache.containsKey(type)) {
          grammarCache.put(type, grammarProvider.apply(type));
       }
       return grammarCache.get(type);
    };
  }
  
}

