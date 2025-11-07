package org.inf.exceptions;

import lombok.Getter;
import org.inf.ty.Ty;

public class InvalidTypeConversionException extends RuntimeException {

  @Getter
  private transient final Ty given;
  @Getter
  private transient final Ty expected;

  public InvalidTypeConversionException(String message, Throwable cause, Ty given, Ty expected) {
    super(message + ". '" + given + "' should be '" + expected + "'", cause);
    this.given = given;
    this.expected = expected;
  }

  public InvalidTypeConversionException(String message, Ty given, Ty expected) {
    this(message, null, given, expected);
  }
}
