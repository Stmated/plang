package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialIdentifier(String name) implements InitialExpression {

  @Override
  public String toString() {
    return "[" + name + "]";
  }

  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitIdentifier(this);
  }
}
