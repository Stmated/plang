package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

public record AstAssignment(AstExpression lhs, AstExpression rhs) implements AstExpression {

  @Override
  public String toString() {
    return lhs + " = " + rhs;
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitAssignment(this);
  }
}
