package com.github.stmated.plang.exceptions;

public abstract class LLVMException extends RuntimeException {

  private final String details;

  public LLVMException(String explanation, String details, Throwable cause) {
    super(explanation, cause);
    this.details = details;
  }

  public String getDetails() {
    return details;
  }

  @Override
  public String toString() {
    if (this.getDetails() != null) {
      return STR."\{this.getMessage()} - \{this.getDetails()}";
    } else {
      return this.getMessage();
    }
  }
}
