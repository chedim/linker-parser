package com.onkiup.linker.parser.util;

import org.apache.log4j.Layout;

import org.apache.log4j.spi.LoggingEvent;

public class LoggerLayout extends Layout {

  private Layout parent;
  private StringBuilder buffer;

  public LoggerLayout(Layout parent, StringBuilder buffer) {
    this.parent = parent;
    this.buffer = buffer;
  }

  @Override
  public String format(LoggingEvent event) {
    CharSequence bufVal = sanitize(buffer.toString());
    return String.format("%s || %s :: %s\n", ralign(bufVal, 50), ralign(event.getLoggerName(), 50), event.getMessage());
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

  public static String ralign(CharSequence what, int len) {
    if (what.length() >= len) {
      what = what.subSequence(what.length() - len, what.length());
      return what.toString();
    }
    String format = String.format("%%%1$d.%1$ds%%2$s", len - what.length());
    return String.format(format, "", what);
  }
}
