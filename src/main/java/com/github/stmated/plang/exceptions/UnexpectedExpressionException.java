package com.github.stmated.plang.exceptions;

public class UnexpectedExpressionException extends RuntimeException {

  private final Object expression;

  public UnexpectedExpressionException(Object expression) {
    this(expression, null);
  }

  public UnexpectedExpressionException(Object expression, Throwable cause) {
    this(STR."Unknown expression: \{expression}", expression, cause);
  }

  public UnexpectedExpressionException(String message, Object expression, Throwable cause) {
    super(message, cause);
    this.expression = expression;
  }

  public Object getExpression() {
    return expression;
  }
}
