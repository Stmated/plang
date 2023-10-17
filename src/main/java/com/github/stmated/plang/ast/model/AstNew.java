package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstNew(
    AstExpression target,
    AstExpression expressions
) implements AstExpression {

  @Override
  public String toString() {
    return "new " + target + "(" + expressions + ")";
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitNew(this);
  }
}
