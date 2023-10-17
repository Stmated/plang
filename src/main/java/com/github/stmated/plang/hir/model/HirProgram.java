package com.github.stmated.plang.hir.model;

public record HirProgram(HirExpression[] expressions) implements HirExpression {

  @Override
  public HirType getResultType() {

    if (this.expressions().length == 0) {
      return HirType.TYPE_UNKNOWN;
    }

    return this.expressions()[this.expressions().length - 1].getResultType();
  }
}
