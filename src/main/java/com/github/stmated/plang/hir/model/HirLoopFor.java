package com.github.stmated.plang.hir.model;

public record HirLoopFor(
  HirVariableDeclaration[] declarations,
  HirExpression predicate,
  HirExpression[] postOps,
  HirExpression body
) implements HirLoop {

  @Override
  public HirType getResultType() {
    return this.body().getResultType();
  }
}
