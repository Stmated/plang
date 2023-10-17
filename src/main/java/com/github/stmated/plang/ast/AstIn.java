package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

public record AstIn(
  AstExpression lhs,
  AstExpression rhs
) implements AstExpression {

  @Override
  public String toString() {
    return lhs + " IN " + rhs;
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitIn(this);
  }
}
