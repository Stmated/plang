package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

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
