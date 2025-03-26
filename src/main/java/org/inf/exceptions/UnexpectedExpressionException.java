package org.inf.exceptions;

public class UnexpectedExpressionException extends RuntimeException {

  private final Object expression;

  public UnexpectedExpressionException(Object expression) {
    this("Unknown expression: " + expression + " of type " + expression.getClass().getSimpleName(), expression, null);
  }

  public UnexpectedExpressionException(Object expression, Throwable cause) {
    this("Unknown expression: " + expression + " of type " + expression.getClass().getSimpleName(), expression, cause);
  }

  public UnexpectedExpressionException(String message, Object expression, Throwable cause) {
    super(message, cause);
    this.expression = expression;
  }

  public Object getExpression() {
    return expression;
  }
}
