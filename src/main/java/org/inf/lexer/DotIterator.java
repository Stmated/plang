package org.inf.lexer;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

class DotIterator implements Iterator<Token> {

  private final Iterator<Token> inner;
  private final Queue<Token> queue = new ArrayDeque<>();

  public DotIterator(final Iterator<Token> inner) {
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

    // Convert DOT, DOT[, DOT] into DOUBLE_DOT or TRIPLE_DOT
    final var token = inner.next();
    if (token.type() == TokenType.DOT && inner.hasNext()) {

      final var token2 = inner.next();
      if (token2 != null && token2.type() == TokenType.DOT && inner.hasNext()) {

        final var token3 = inner.next();
        if (token3 != null && token3.type() == TokenType.DOT) {

          final var content = token.content() + token2.content() + token3.content();
          return new Token(TokenType.TRIPLE_DOT, token.start(), token3.end(), content);
        } else {

          queue.add(token3);
          final var content = token.content() + token2.content();
          return new Token(TokenType.DOUBLE_DOT, token.start(), token2.end(), content);
        }
      } else {
        queue.add(token2);
        return token;
      }
    }

    return token;
  }
}
