package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialTypePlaceholder(
  InitialIdentifier identifier
) implements InitialExpression {

  @Override
  public String toString() {
    return "$" + identifier;
  }

  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitTypePlaceholder(this);
  }
}
