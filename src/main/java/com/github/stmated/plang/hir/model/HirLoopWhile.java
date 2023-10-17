package com.github.stmated.plang.hir.model;

public record HirLoopWhile(HirExpression predicate, HirExpression body) implements HirLoop {

  @Override
  public HirType getResultType() {
    return this.body().getResultType();
  }
}
