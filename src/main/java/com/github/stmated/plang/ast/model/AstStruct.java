package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstStruct(
    AstBlock block
) implements AstExpression {
  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitStruct(this);
  }
}
