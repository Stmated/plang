package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialMatch(
    InitialExpression target,
    InitialExpressionCollection children
) implements InitialExpression {
  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitMatch(this);
  }
}
