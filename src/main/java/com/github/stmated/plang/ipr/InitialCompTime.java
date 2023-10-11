package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialCompTime(
    InitialExpression target
) implements InitialExpression {

  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitCompTime(this);
  }
}
