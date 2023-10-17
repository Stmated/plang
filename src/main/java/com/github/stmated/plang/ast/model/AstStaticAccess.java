package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstStaticAccess(
  AstExpression lhs,
  AstExpression rhs
) implements AstExpression {

  @Override
  public String toString() {
    return lhs + "::" + rhs;
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitStaticAccess(this);
  }
}
