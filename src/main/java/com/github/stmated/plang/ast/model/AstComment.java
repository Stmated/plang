package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstComment(String content) implements AstExpression {

  @Override
  public String toString() {
    return "/*" + content + " */";
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitComment(this);
  }
}
