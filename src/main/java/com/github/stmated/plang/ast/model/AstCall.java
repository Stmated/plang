package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstCall(
  AstExpression target,
  AstParen paren,
  boolean onErrorBubbleUp,
  boolean partial
) implements AstExpression {

  @Override
  public String toString() {
    return target +
      (partial ? "~" : "") +
      "(" + paren + ")" + (onErrorBubbleUp ? "!" : "");
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitCall(this);
  }
}
