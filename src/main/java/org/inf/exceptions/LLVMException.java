package org.inf.exceptions;

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
      return this.getMessage() + " - " + this.getDetails();
    } else {
      return this.getMessage();
    }
  }
}
