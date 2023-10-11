package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialNoOp() implements InitialExpression {

  @Override
  public String toString() {
    return "NoOp";
  }

  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitNoOp(this);
  }
}
