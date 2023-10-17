package com.github.stmated.plang.lexer;

public record Token(TokenType type, int start, int end, String content) {

  @Override
  public String toString() {
    return "{%s %d:%d %s}".formatted(type, start, end, content);
  }
}
