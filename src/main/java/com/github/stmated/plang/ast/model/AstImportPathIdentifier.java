package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstImportPathIdentifier(
    AstIdentifier identifier
) implements AstExpression, AstImportCapable {
  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitImportPathIdentifier(this);
  }
}
