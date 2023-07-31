package com.github.stmated.plang.hir;

public record BinaryOperation(Expression lhs, BinaryOperationKind type, Expression rhs) implements Expression {

  @Override
  public Type getResultType() {
    return Type.getCommonDenominator(this.lhs().getResultType(), this.rhs().getResultType());
  }
}
