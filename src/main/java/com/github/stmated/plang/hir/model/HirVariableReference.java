package com.github.stmated.plang.hir.model;

public record HirVariableReference(HirVariableDeclaration declaration) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//    return this.declaration().getResultType();
//  }
}
