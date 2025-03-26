package org.inf.exceptions;

public class UncaughtLLVMException extends LLVMException {

  public UncaughtLLVMException(String message, Throwable cause) {
    super(message, null, cause);
  }
}
