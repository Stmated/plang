package com.github.stmated.plang.hir;

public record Not(Expression expression) implements Expression {

  @Override
  public Type getResultType() {
    return Type.getInvertedType(this.expression().getResultType());
  }
}
