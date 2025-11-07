package org.inf.exceptions;

import lombok.Getter;
import org.inf.util.TextRange;

public class LexerException extends RuntimeException {

  @Getter
  private final TextRange location;

  public LexerException(String message, TextRange range) {
    super(message);
    this.location = range;
  }

  public LexerException(String message, Throwable cause, TextRange location) {
    super(message, cause);
    this.location = location;
  }
}
