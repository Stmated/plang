package com.github.stmated.plang.exceptions;

import com.github.stmated.plang.util.TextRange;
import lombok.Getter;

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
