package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialParen(InitialExpression expression) implements InitialExpression {
  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitParen(this);
  }
}
