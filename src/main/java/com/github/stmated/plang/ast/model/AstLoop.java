package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstLoop(
  AstExpression body
) implements AstExpression {

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitLoop(this);
  }
}
