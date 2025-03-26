package org.inf.exceptions;

public class GenericLLVMException extends LLVMException {

  public GenericLLVMException(String message, String details) {
    super(message, details, null);
  }
}
