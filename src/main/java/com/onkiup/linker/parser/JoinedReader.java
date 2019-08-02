package com.onkiup.linker.parser;

import java.io.IOException;
import java.io.Reader;

public class JoinedReader extends Reader {

  private Reader[] readers;
  private int currentReader = 0;

  public JoinedReader(Reader... readers) {
    this.readers = readers;
  }

  @Override
  public void close() throws IOException {
    for (Reader reader: readers) {
      reader.close();
    }
  }

  @Override
  public int read(char[] buffer, int off, int len) throws IOException {
    if (currentReader < readers.length) {
      int read = 0;
      while (read < len && currentReader < readers.length) {
        int readerRead = readers[currentReader].read(buffer, off + read, len - read);
        if (readerRead == -1) {
          currentReader++;
        } else {
          read += readerRead;
        }
      }
      return read;
    }

    return -1;
  }

}
