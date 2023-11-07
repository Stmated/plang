package com.github.stmated.plang.exceptions;

import com.github.stmated.plang.util.TextRange;

public class InvalidDecimalsException extends LexerException {

  public InvalidDecimalsException(String message, TextRange range) {
    super(message, range);
  }

  public InvalidDecimalsException(String message, Throwable cause, TextRange location) {
    super(message, cause, location);
  }
}
