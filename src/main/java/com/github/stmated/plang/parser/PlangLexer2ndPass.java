package com.github.stmated.plang.parser;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

/**
 * Will convert things like [LITERAL_INTEGER, DOT, LITERAL_INTEGER] into LITERAL_DECIMAL
 * But will still make [LITERAL_INTEGER, DOT, DOT, LITERAL_INTEGER] into [LITERAL_INTEGER, DOUBLE_DOT, LITERAL_INTEGER]
 */
public class PlangLexer2ndPass {

  public Iterator<Token> transform(Iterator<Token> tokens) {

    return new DecimalIterator(
      new DotIterator(tokens)
    );
  }

  private static class DotIterator implements Iterator<Token> {

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

      // Convert DOT, DOT, DOT into DOUBLE_DOT or TRIPLE_DOT
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

  private static class DecimalIterator implements Iterator<Token> {

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
      final var token = inner.next();
      if (token != null) {
        if (token.type() == TokenType.DOT && inner.hasNext()) {
          final var token2 = inner.next();
          if (token2 != null && token2.type() == TokenType.LITERAL_INTEGER) {

            final var content = token.content() + token2.content();
            return new Token(TokenType.LITERAL_DECIMAL, token.start(), token2.end(), content);

          } else if (token2 != null) {
            queue.add(token2);
            return token;
          }
        } else if (token.type() == TokenType.LITERAL_INTEGER && inner.hasNext()) {

          final var token2 = inner.next();
          if (token2 != null && token2.type() == TokenType.DOT) {

            final var token3 = inner.hasNext() ? inner.next() : null;
            if (token3 != null && token3.type() == TokenType.LITERAL_INTEGER) {

              final var content = token.content() + token2.content() + token3.content();
              return new Token(TokenType.LITERAL_DECIMAL, token.start(), token3.end(), content);
            } else {

              // TODO: Is this also a decimal? "1."
              queue.add(token2);
              queue.add(token3);
              return token;
            }
          } else {
            queue.add(token2);
            return token;
          }
        }
      }

      return token;
    }
  }
}
