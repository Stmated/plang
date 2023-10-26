package com.github.stmated.plang.exceptions;

public class UnreachableCodeLLVMException extends LLVMException {

  public UnreachableCodeLLVMException(String message, String details) {
    super(message, details, null);
  }
}
