package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstLoopDoWhile(
    AstExpression body,
    AstExpression predicate
) implements AstExpression {
  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitLoopDoWhile(this);
  }
}
