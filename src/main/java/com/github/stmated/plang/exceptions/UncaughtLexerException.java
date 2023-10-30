package com.github.stmated.plang.exceptions;

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
