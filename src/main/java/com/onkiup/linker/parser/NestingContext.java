package com.onkiup.linker.parser;

import java.util.HashMap;

// since 0.2.2
public class NestingContext {
  private SubContext context;

  public NestingContext() {
    this.context = new SubContext("<[ROOT]>");
  }

  public Object get(String key) {
    return context.member(key);
  }

  public void set(String name, Object value) {
    context.member(name, value);
  }

  public void push(String name) {
    context = new SubContext(name, context);
  }

  public void pop() {
    context = context.parent();
  }

  public SubContext dump() {
    return context;
  }

  private class SubContext {
    private final String name;
    private final SubContext parent;
    private final HashMap<String, Object> members = new HashMap<>();

    public SubContext(String name) {
      this(name, null);
    }

    public SubContext(String name, SubContext parent) {
      this.name = name;
      this.parent = parent;
    }

    public SubContext subContext(String name) {
      return new SubContext(name, this);
    }

    public String name() {
      return name;
    }

    public Object member(String name) {
      if (!members.containsKey(name)) {
        if (parent != null) {
          return parent.member(name);
        } else {
          throw new UnknownReference(name);
        }
      }
      return members.get(name);
    }

    public void member(String name, Object value) {
      members.put(name, value);
    }
    
    public boolean isMember(String name) {
      return members.containsKey(name);
    }

    public boolean isReferable(String name) {
      return isMember(name) || (parent != null && parent.isReferable(name));
    }

    public SubContext parent() {
      if (parent == null) {
        throw new RuntimeException("Unable to return parent context: already at root");
      }
      return parent;
    }
  }
}

