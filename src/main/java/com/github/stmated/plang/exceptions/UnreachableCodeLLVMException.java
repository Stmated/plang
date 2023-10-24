package com.github.stmated.plang.exceptions;

public class UnreachableCodeLLVMException extends RuntimeException {

  private final String explanation;

  public UnreachableCodeLLVMException(String explanation) {
    this.explanation = explanation;
  }

  public String getExplanation() {
    return explanation;
  }
}
