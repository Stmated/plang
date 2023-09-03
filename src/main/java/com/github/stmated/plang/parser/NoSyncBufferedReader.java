package com.github.stmated.plang.parser;

import java.io.IOException;
import java.io.Reader;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

public class NoSyncBufferedReader implements AutoCloseable {

  private static final ObjectPool<char[]> POOL;

  static {

    POOL = new GenericObjectPool<>(new BasePooledObjectFactory<>() {

      @Override
      public char[] create() {
        return new char[8192];
      }

      @Override
      public PooledObject<char[]> wrap(final char[] obj) {
        return new DefaultPooledObject<>(obj);
      }
    });
  }

  private final Reader reader;
  private final char[] buffer;
  private int idx, limit;

  public NoSyncBufferedReader(Reader reader) {
    this.reader = reader;

    try {
      this.buffer = POOL.borrowObject();
    } catch (Exception ex) {
      throw new RuntimeException("Could not borrow a new pooled buffer", ex);
    }
  }

  public int read() throws IOException {
    if (idx >= limit) {
      limit = reader.read(buffer);
      if (limit <= 0) return -1;
      idx = 0;
    }

    return buffer[idx++];
  }

  public void close() throws IOException {
    reader.close();

    try {
      POOL.returnObject(buffer);
    } catch (Exception ex) {
      throw new RuntimeException("Could not return the pooled buffer", ex);
    }
  }
}
