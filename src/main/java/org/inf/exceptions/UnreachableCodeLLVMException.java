package org.inf.exceptions;

public class UnreachableCodeLLVMException extends LLVMException {

  public UnreachableCodeLLVMException(String message, String details) {
    super(message, details, null);
  }
}
