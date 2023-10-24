package com.github.stmated.plang.exceptions;

public class UncaughtLLVMException extends RuntimeException {

  private final String explanation;

  public UncaughtLLVMException(String explanation, Throwable cause) {
    super(cause);
    this.explanation = explanation;
  }

  public String getExplanation() {
    return explanation;
  }
}
