package com.github.stmated.plang.hir.model;

public record HirParameter(HirIdentifier identifier, HirType type) implements HirExpression {

  @Override
  public HirType getResultType() {
    return this.type();
  }
}
