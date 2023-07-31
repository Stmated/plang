package com.github.stmated.plang.hir;

public record Program(Expression[] expressions) implements Expression {

  @Override
  public Type getResultType() {

    if (this.expressions().length == 0) {
      return Type.TYPE_UNKNOWN;
    }

    return this.expressions()[this.expressions().length - 1].getResultType();
  }
}
