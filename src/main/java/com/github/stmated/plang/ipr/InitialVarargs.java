package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

/**
 * TODO: This is a weird node -- remove it and make it a boolean on some other node?
 */
public record InitialVarargs() implements InitialExpression {

  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitVarargs(this);
  }
}
