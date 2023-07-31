package com.github.stmated.plang.hir;

public record LoopWhile(Expression predicate, Expression body) implements Loop {

  @Override
  public Type getResultType() {
    return this.body().getResultType();
  }
}
