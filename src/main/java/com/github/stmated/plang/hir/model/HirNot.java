package com.github.stmated.plang.hir.model;

public record HirNot(HirExpression expression) implements HirExpression {

  @Override
  public HirType getResultType() {
    return HirType.getInvertedType(this.expression().getResultType());
  }
}
