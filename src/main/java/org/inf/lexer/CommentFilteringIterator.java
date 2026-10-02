package org.inf.lexer;

import java.util.Iterator;
import java.util.NoSuchElementException;

public final class CommentFilteringIterator implements Iterator<Token> {

  private final Iterator<Token> source;
  private Token next;

  public CommentFilteringIterator(final Iterator<Token> source) {
    this.source = source;
  }

  @Override
  public boolean hasNext() {
    while (next == null && source.hasNext()) {
      final var token = source.next();
      if (!token.type().isComment()) {
        next = token;
      }
    }
    return next != null;
  }

  @Override
  public Token next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    final var token = next;
    next = null;
    return token;
  }
}
