package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialLoopWhile(
    InitialExpression predicate,
    InitialExpression body
) implements InitialExpression {
  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitLoopWhile(this);
  }
}
