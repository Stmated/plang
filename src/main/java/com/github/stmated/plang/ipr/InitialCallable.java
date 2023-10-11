package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialCallable(InitialExpression lhs, InitialExpression rhs) implements InitialExpression {

  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitCallable(this);
  }
}
