package com.github.stmated.plang.hir.model;

public record HirRange(HirExpression lower, HirExpression higher) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//
//    final var commonType = HirType.getCommonDenominator(lower.getResultType(), higher.getResultType());
//    final var expectedSize = HirType.getAssumedLengthBetween(commonType, lower.getResultType(), higher.getResultType());
//
//    return HirType.asArray(commonType, expectedSize);
//  }
}
