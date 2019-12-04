package com.onkiup.linker.parser;

import java.io.Reader;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Stream;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

public class ParserContext<X extends Rule> implements LinkerParser<X> {

  private static InheritableThreadLocal<ParserContext> INSTANCE = new InheritableThreadLocal<>();

  private Reflections reflections = new Reflections(new ConfigurationBuilder()
      .setUrls(ClasspathHelper.forClassLoader(TokenGrammar.class.getClassLoader()))
      .setScanners(new SubTypesScanner(true))
  );

  private Class<? extends Extension<X>> extension;

  private Map<Class, Class> extensions = new WeakHashMap<>();

  private Class<X> target;

  private TokenGrammar<X> grammar;

  public static ParserContext<?> get() {
    ParserContext instance = INSTANCE.get();
    if (instance == null) {
      instance = new ParserContext();
      INSTANCE.set(instance);
    }
    return instance;
  }

  public <X extends Rule> Stream<Class<? extends X>> implementations(Class<X> junction) {
    return subClasses(junction)
        .filter(TokenGrammar::isConcrete)
        .filter(TokenGrammar::isRule);
  }

  public <X> Stream<Class<? extends X>> subClasses(Class<X> parent) {
    return reflections.getSubTypesOf(parent).stream();
  }

  /**
   * This method can be used to limit the scope in which grammar tokens are looked up for
   * @param classLoader a classloader to take classpath from
   */
  public void classLoader(ClassLoader classLoader) {
    reflections = new Reflections(new ConfigurationBuilder()
        .setUrls(ClasspathHelper.forClassLoader(classLoader))
        .setScanners(new SubTypesScanner(true))
    );
  }

  @Override
  public LinkerParser<X> target(Class<X> target) {
    this.target = target;
    this.grammar = TokenGrammar.forClass(target);
    return this;
  }

  @Override
  public X parse(String sourceName, Reader from) {
    if (grammar == null) {
      grammar = TokenGrammar.forClass(target);
    }
    return grammar.tokenize(sourceName, from);
  }
}
