package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

import java.util.Arrays;

public record InitialCall(
  InitialExpression target,
  InitialParen paren,
  boolean onErrorBubbleUp,
  boolean partial
) implements InitialExpression {

  @Override
  public String toString() {
    return target +
      (partial ? "~" : "") +
      "(" + paren + ")" + (onErrorBubbleUp ? "!" : "");
  }

  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitCall(this);
  }
}
