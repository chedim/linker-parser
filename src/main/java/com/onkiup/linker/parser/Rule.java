package com.onkiup.linker.parser;

public interface Rule<C, O> {
  default O evaluate(C context) {
    throw new RuntimeException("Not implemented");
  }

  default String transpile() {
    throw new RuntimeException("Not implemented");
  }
}

