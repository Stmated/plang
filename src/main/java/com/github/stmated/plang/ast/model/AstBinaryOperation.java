package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstBinaryOperation(
  AstExpression lhs,
  AstBinaryOperationKind type,
  AstExpression rhs
) implements AstExpression {

  @Override
  public String toString() {
    return lhs + " " + type + " " + rhs;
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitBinaryOperation(this);
  }
}
