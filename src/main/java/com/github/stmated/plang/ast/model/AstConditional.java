package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstConditional(
    AstExpression predicate,
    AstExpression pass,
    AstExpression fail
) implements AstExpression {
  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitConditional(this);
  }
}
