package com.github.stmated.plang.hir;

public record Argument(Expression expression) implements Expression {

  @Override
  public Type getResultType() {
    return this.expression().getResultType();
  }
}
