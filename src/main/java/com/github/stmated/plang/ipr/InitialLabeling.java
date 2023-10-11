package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialLabeling(
  InitialExpression lhs,
  InitialExpression rhs
) implements InitialExpression {

  @Override
  public String toString() {
    return lhs + ": " + rhs;
  }

  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitLabeling(this);
  }
}
