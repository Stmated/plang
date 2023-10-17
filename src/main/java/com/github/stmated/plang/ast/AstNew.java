package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

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
