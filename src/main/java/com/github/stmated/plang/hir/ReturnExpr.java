package com.github.stmated.plang.hir;

public record ReturnExpr(Expression expression) implements Expression {

  @Override
  public Type getResultType() {
    return this.expression().getResultType();
  }
}
