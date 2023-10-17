package com.github.stmated.plang.hir.model;

public record HirGenericParameterReference(HirGenericParameterDeclaration declaration) implements HirExpression {

  @Override
  public HirType getResultType() {
    return this.declaration().getResultType();
  }
}
