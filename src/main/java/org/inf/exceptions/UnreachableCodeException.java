package org.inf.exceptions;

public final class UnreachableCodeException extends IllegalArgumentException {

  public UnreachableCodeException(String message) {
    super(message);
  }
}
