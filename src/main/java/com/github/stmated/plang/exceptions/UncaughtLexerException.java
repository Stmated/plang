package com.github.stmated.plang.exceptions;

import com.github.stmated.plang.lexer.Token;

public class UncaughtLexerException extends RuntimeException {

  private final String explanation;

  public UncaughtLexerException(String explanation, Throwable cause) {
    super(cause);
    this.explanation = explanation;
  }

  public String getExplanation() {
    return explanation;
  }
}
