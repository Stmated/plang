package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialBinaryOperation(
  InitialExpression lhs,
  InitialBinaryOperationType type,
  InitialExpression rhs
) implements InitialExpression {

  @Override
  public String toString() {
    return lhs + " " + type + " " + rhs;
  }

  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitBinaryOperation(this);
  }
}
