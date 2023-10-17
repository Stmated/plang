package com.github.stmated.plang.hir.model;

public record HirFunction(HirIdentifier identifier, HirParameter[] parameters, HirType returnType) implements HirExpression {

  @Override
  public HirType getResultType() {
    return this.returnType();
  }
}
