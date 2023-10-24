package com.github.stmated.plang.exceptions;

public class GenericLLVMException extends RuntimeException {

  private final String explanation;

  public GenericLLVMException(String explanation) {
    this.explanation = explanation;
  }

  public String getExplanation() {
    return explanation;
  }
}
