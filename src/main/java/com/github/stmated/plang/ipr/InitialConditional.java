package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialConditional(
    InitialExpression predicate,
    InitialExpression pass,
    InitialExpression fail
) implements InitialExpression {
  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitConditional(this);
  }
}
