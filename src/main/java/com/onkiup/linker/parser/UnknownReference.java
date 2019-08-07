package com.onkiup.linker.parser;

public class UnknownReference extends RuntimeException {

  public UnknownReference(String reference) {
    super(reference);
  }
}

