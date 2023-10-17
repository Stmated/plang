package com.github.stmated.plang.lexer;

import com.github.stmated.plang.parser.Token;
import com.github.stmated.plang.parser.TokenType;
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
    if (t != null) {
      if (t.type() == TokenType.DOT && inner.hasNext()) {
        final var t2 = inner.next();
        if (t2 != null && t2.type() == TokenType.LITERAL_INTEGER) {

          final var content = t.content() + t2.content();
          return new Token(TokenType.LITERAL_DECIMAL, t.start(), t2.end(), content);

        } else if (t2 != null) {
          queue.add(t2);
          return t;
        }
      } else if (t.type() == TokenType.LITERAL_INTEGER && inner.hasNext()) {

        final var t2 = inner.next();
        if (t2 != null && t2.type() == TokenType.DOT) {

          final var t3 = inner.hasNext() ? inner.next() : null;
          if (t3 != null && t3.type() == TokenType.LITERAL_INTEGER) {

            final var content = t.content() + t2.content() + t3.content();
            return new Token(TokenType.LITERAL_DECIMAL, t.start(), t3.end(), content);
          } else {

            // TODO: Is this also a decimal? "1."
            queue.add(t2);
            queue.add(t3);
            return t;
          }
        } else {
          queue.add(t2);
          return t;
        }
      }
    }

    return t;
  }
}
