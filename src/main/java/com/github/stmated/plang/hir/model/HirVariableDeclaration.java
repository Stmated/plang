package com.github.stmated.plang.hir.model;

public record HirVariableDeclaration(HirIdentifier identifier, HirMutabilityKind mutabilityKind, HirType type) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//    return this.type();
//  }
}
