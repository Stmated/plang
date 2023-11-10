package com.github.stmated.plang.hir.model;

public record HirBinaryOperation(HirExpression lhs, HirBinaryOperationKind kind, HirExpression rhs) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//    return HirType.getCommonDenominator(this.lhs().getResultType(), this.rhs().getResultType());
//  }


  @Override
  public String toString() {
    return STR."\{this.lhs()} \{kind} \{this.rhs()}";
  }
}
