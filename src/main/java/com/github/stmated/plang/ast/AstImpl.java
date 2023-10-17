package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

public record AstImpl(
  AstIdentifier traitIdentifier,
  AstExpression forExpression,
  AstBlock block,
  AstExpression[] with
) implements AstExpression {

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitImpl(this);
  }
}
