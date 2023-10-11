package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

import java.util.Arrays;

public record InitialExpressionCollection<T extends InitialExpression>(
    T[] children
) implements InitialExpression {

  @Override
  public String toString() {
    return Arrays.toString(children);
  }

  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitExpressionCollection(this);
  }
}
