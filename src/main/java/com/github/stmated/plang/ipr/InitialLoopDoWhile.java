package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialLoopDoWhile(
    InitialExpression body,
    InitialExpression predicate
) implements InitialExpression {
  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitLoopDoWhile(this);
  }
}
