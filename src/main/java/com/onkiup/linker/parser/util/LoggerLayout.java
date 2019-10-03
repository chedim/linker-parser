package com.onkiup.linker.parser.util;

import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.log4j.Layout;

import org.apache.log4j.spi.LoggingEvent;

public class LoggerLayout extends Layout {

  private Layout parent;
  private CharSequence buffer;
  private Supplier<Integer> position;

  public LoggerLayout(Layout parent, CharSequence buffer, Supplier<Integer> position) {
    this.parent = parent;
    this.buffer = buffer;
    this.position = position;
  }

  public static CharSequence repeat(CharSequence s, int times) {
    if (times < 1) {
      return "";
    }
    return IntStream.of(times).boxed()
        .map(i -> s)
        .collect(Collectors.joining());
  }

  @Override
  public String format(LoggingEvent event) {
    int position = this.position.get();
    CharSequence bufVal = buffer;
    if (position < buffer.length()) {
      bufVal = buffer.subSequence(Math.max(0, position - 50), position);
    }
    bufVal = String.format("'%s'", ralign(sanitize(bufVal), 48));
    return String.format("%50.50s || %s :: %s\n", bufVal, ralign(event.getLoggerName(), 50), event.getMessage());
  }

  @Override
  public boolean ignoresThrowable() {
    return parent.ignoresThrowable();
  }

  @Override
  public void activateOptions() {
    parent.activateOptions();
  }

  public Layout parent() {
    return parent;
  }

  public static String sanitize(Object what) {
    return what == null ? "null" : sanitize(what.toString());
  }
  public static String sanitize(String what) {
    return what == null ? null : what.replaceAll("\n", "\\\\n").replaceAll("\t", "\\\\t");
  }

  public static CharSequence head(CharSequence what, int len) {
    if (what.length() < len) {
      return what;
    }
    return what.subSequence(0, len);
  }

  public static String ralign(CharSequence what, int len) {
    if (what.length() >= len) {
      what = what.subSequence(what.length() - len, what.length());
      return what.toString();
    }
    String format = String.format("%%%1$d.%1$ds%%2$s", len - what.length());
    return String.format(format, "", what);
  }
}
