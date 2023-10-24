package com.github.stmated.plang.exceptions;

import com.github.stmated.plang.lexer.Token;

public class UnexpectedTokenException extends RuntimeException {

  private final transient Token token;

  public UnexpectedTokenException(Token token) {
    this.token = token;
  }

  public Token getToken() {
    return token;
  }
}
