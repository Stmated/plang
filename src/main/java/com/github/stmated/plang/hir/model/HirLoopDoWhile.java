package com.github.stmated.plang.hir.model;

public record HirLoopDoWhile(HirExpression predicate, HirExpression body) implements HirLoop {

  @Override
  public HirType getResultType() {
    return this.body().getResultType();
  }
}
