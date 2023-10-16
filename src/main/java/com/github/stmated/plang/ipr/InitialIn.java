package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialIn(
  InitialExpression lhs,
  InitialExpression rhs
) implements InitialExpression {

  @Override
  public String toString() {
    return lhs + " IN " + rhs;
  }

  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitIn(this);
  }
}
