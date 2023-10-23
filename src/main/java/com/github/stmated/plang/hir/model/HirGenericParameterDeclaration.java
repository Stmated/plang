package com.github.stmated.plang.hir.model;

public record HirGenericParameterDeclaration(HirIdentifier identifier, HirType lowerBound, HirType higherBound) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//
//    if (this.lowerBound == null) {
//      return HirType.TYPE_UNKNOWN;
//    }
//
//    return this.lowerBound;
//  }
}
