package com.github.stmated.plang.hir;

public record Range(Expression lower, Expression higher) implements Expression {

  @Override
  public Type getResultType() {

    final var commonType = Type.getCommonDenominator(lower.getResultType(), higher.getResultType());
    final var expectedSize = Type.getAssumedLengthBetween(commonType, lower.getResultType(), higher.getResultType());

    return Type.asArray(commonType, expectedSize);
  }
}
