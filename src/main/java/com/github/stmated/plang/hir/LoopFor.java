package com.github.stmated.plang.hir;

public record LoopFor(VariableDeclaration[] declarations, Expression predicate, Expression[] postOps, Expression body) implements Loop {

  @Override
  public Type getResultType() {
    return this.body().getResultType();
  }
}
