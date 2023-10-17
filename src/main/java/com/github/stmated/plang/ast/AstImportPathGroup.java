package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

public record AstImportPathGroup(
    AstImportCapable[] items
) implements AstExpression, AstImportCapable {
  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitImportPathGroup(this);
  }
}
