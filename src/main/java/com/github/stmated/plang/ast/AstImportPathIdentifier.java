package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

public record AstImportPathIdentifier(
    AstIdentifier identifier
) implements AstExpression, AstImportCapable {
  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitImportPathIdentifier(this);
  }
}
