package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

public record AstTypePlaceholder(
  AstIdentifier identifier
) implements AstExpression {

  @Override
  public String toString() {
    return "$" + identifier;
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitTypePlaceholder(this);
  }
}
