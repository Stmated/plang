package org.inf.lexer;

import org.inf.exceptions.UnexpectedTokenException;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

class DecimalIterator implements Iterator<Token> {

  private final Iterator<Token> inner;
  private final Queue<Token> queue = new ArrayDeque<>();

  public DecimalIterator(final Iterator<Token> inner) {
    this.inner = inner;
  }

  @Override
  public boolean hasNext() {

    if (!queue.isEmpty()) {
      return true;
    }

    return this.inner.hasNext();
  }

  @Override
  public Token next() {

    if (!queue.isEmpty()) {
      return queue.poll();
    }

    // Convert integers with decimals into decimals
    final var t = inner.next();
    if (t != null && t.type() == TokenType.DOT && inner.hasNext()) {
      final var t2 = inner.next();
      if (t2 != null && t2.type() == TokenType.LITERAL_INTEGER) {

        final var content = t.content() + t2.content();

        if (inner.hasNext()) {
          final var t3 = inner.next();
          if (t3.type() == TokenType.DOT) {
            // ".123." is not valid syntax. Fail early.
            // TODO: This should change. Should be up to parser whether it's useful or not.
            throw new UnexpectedTokenException(t3);
          } else {
            queue.add(t3);
          }
        }

        return new Token(TokenType.LITERAL_DOUBLE, t.start(), t2.end(), content);

      } else if (t2 != null) {
        queue.add(t2);
        return t;
      }
    } else if (t != null && t.type() == TokenType.LITERAL_INTEGER && inner.hasNext()) {

      final var t2 = inner.next();
      if (t2 != null && t2.type() == TokenType.DOT) {

        final var t3 = inner.hasNext() ? inner.next() : null;
        if (t3 != null && t3.type() == TokenType.LITERAL_INTEGER) {

          final var content = t.content() + t2.content() + t3.content();

          if (inner.hasNext()) {
            final var t4 = inner.next();
            if (t4.type() == TokenType.DOT) {
              // "123.123." is not valid syntax. Fail early.
              // TODO: This should change. Should be up to parser whether it's useful or not.
              throw new UnexpectedTokenException(t4);
            } else {
              queue.add(t4);
            }
          }

          return new Token(TokenType.LITERAL_DOUBLE, t.start(), t3.end(), content);
        } else {

          queue.add(t2);
          queue.add(t3);
          return t;
        }
      } else {
        queue.add(t2);
        return t;
      }
    }

    return t;
  }
}
