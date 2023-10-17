package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstNoOp() implements AstExpression {

  @Override
  public String toString() {
    return "NoOp";
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitNoOp(this);
  }
}
