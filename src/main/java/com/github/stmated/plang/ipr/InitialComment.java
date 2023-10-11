package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialComment(String content) implements InitialExpression {

  @Override
  public String toString() {
    return "/*" + content + " */";
  }

  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitComment(this);
  }
}
