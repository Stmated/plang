package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

public record AstLoopWhile(
    AstExpression predicate,
    AstExpression body
) implements AstExpression {

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitLoopWhile(this);
  }
}
