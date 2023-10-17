package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

public record AstImport(
    AstImportCapable path
) implements AstExpression {

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitImport(this);
  }
}
