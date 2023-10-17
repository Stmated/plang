package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

public record AstImportPathAlias(
    AstIdentifier alias,
    AstImportCapable target
) implements AstExpression, AstImportCapable {
  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitImportPathAlias(this);
  }
}
