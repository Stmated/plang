package com.github.stmated.plang.exceptions;

public class UncaughtLLVMException extends LLVMException {

  public UncaughtLLVMException(String message, Throwable cause) {
    super(message, null, cause);
  }
}
