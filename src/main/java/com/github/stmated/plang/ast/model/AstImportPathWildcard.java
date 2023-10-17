package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstImportPathWildcard() implements AstExpression, AstImportCapable {
  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitImportPathWildcard(this);
  }
}
