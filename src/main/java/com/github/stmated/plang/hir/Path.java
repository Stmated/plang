package com.github.stmated.plang.hir;

public record Path(Expression owner, Expression member) implements Expression {

  @Override
  public Type getResultType() {
    return this.member().getResultType();
  }
}
