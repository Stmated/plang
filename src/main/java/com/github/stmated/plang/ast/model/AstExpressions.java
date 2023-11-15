package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

import java.util.Arrays;

public record AstExpressions(
  AstExpression[] children
) implements AstExpression {

  @Override
  public String toString() {
    return Arrays.toString(children);
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitExpressionCollection(this);
  }
}
