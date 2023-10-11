package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialNew(
    InitialExpression target,
    InitialExpression expressions
) implements InitialExpression {

  @Override
  public String toString() {
    return "new " + target + "(" + expressions + ")";
  }

  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitNew(this);
  }
}
