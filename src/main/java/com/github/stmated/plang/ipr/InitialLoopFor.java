package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialLoopFor(
    InitialAssignment[] assignments,
    InitialExpression predicate,
    InitialExpression[] steppers,
    InitialBlock block
) implements InitialExpression {
  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitLoopFor(this);
  }
}
