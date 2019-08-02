package com.onkiup.linker.parser;

import java.io.StringReader;

import org.junit.Assert;
import org.junit.Test;

public class NestingReaderTest {
    
  @Test
  public void testReading() throws Exception {
    NestingReader reader = new NestingReader(new StringReader("TEST"));

    Assert.assertEquals('T', reader.read());
    Assert.assertEquals('E', reader.read());
    Assert.assertEquals('S', reader.read());
    Assert.assertEquals('T', reader.read());
    Assert.assertEquals(-1, reader.read());
    Assert.assertEquals(-1, reader.read());

    reader.close();
  }

  @Test
  public void testCancelledNesting() throws Exception {
    NestingReader reader = new NestingReader(new StringReader("TEST"));

    Assert.assertEquals('T', reader.read());

    reader.nest(sub -> {
      try {
      Assert.assertEquals('E', reader.read());
      return false;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Assert.assertEquals('E', reader.read());

    reader.close();
  }

  @Test
  public void testAcceptedNesting() throws Exception {
    NestingReader reader = new NestingReader(new StringReader("TEST"));

    Assert.assertEquals('T', reader.read());

    reader.nest(sub -> {
      try {
        Assert.assertEquals('E', sub.read());
        return true;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Assert.assertEquals('S', (char) reader.read());

    reader.close();
  }

  @Test
  public void testCanncelledAndThenAcceptedNesting() throws Exception {
    NestingReader reader = new NestingReader(new StringReader("ABCDEFG"));

    Assert.assertEquals('A', reader.read());

    reader.nest(sub -> {
      try {
        Assert.assertEquals('B', reader.read());
        return false;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Assert.assertEquals('B', reader.read());

    reader.nest(sub -> {
      try {
        Assert.assertEquals('C', reader.read());
        return true;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Assert.assertEquals('D', reader.read());

    reader.close();
  }

  @Test
  public void testPushBack() throws Exception {
    NestingReader reader = new NestingReader(new StringReader("ABCDEFG"));

    reader.pushBack('a');
    Assert.assertEquals('a', reader.read());
    Assert.assertEquals('A', reader.read());
    Assert.assertEquals(0, reader.getPositionOffset());

    reader.nest(sub -> {
        try {
          Assert.assertEquals('B', sub.read());
          sub.pushBack('b');
          Assert.assertEquals('b', sub.read());
          Assert.assertEquals(2, sub.getPositionOffset());
          return false;
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    Assert.assertEquals(0, reader.getPositionOffset());
    Assert.assertEquals('B', reader.read());
    Assert.assertEquals('b', reader.read());

    reader.nest(sub -> {
      try {
        Assert.assertEquals('C', sub.read());
        sub.pushBack('c');
        Assert.assertEquals('c', sub.read());
        return true;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Assert.assertEquals('D', reader.read());

    reader.nest(sub -> {
      try {
        Assert.assertEquals('E', sub.read());
        sub.pushBack('e');
        sub.nest(subsub -> {
          try {
            Assert.assertEquals('e', subsub.read());
            subsub.pushBack('1');
            return false;
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
        Assert.assertEquals('e', sub.read());
        Assert.assertEquals('1', sub.read());

        sub.nest(subsub -> {
          try {
            Assert.assertEquals('F', subsub.read());
            subsub.pushBack('2');
            return true;
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });

        Assert.assertEquals('2', sub.read());
        return false;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Assert.assertEquals('E', reader.read());
    Assert.assertEquals('e', reader.read());
    Assert.assertEquals('1', reader.read());
    Assert.assertEquals('F', reader.read());
    Assert.assertEquals('2', reader.read());

    reader.close();
  }
}
