package com.github.stmated.plang.hir.model;

public record HirBinaryOperation(HirExpression lhs, HirBinaryOperationKind type, HirExpression rhs) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//    return HirType.getCommonDenominator(this.lhs().getResultType(), this.rhs().getResultType());
//  }


}
