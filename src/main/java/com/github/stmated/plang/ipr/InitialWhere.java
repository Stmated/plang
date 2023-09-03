package com.github.stmated.plang.ipr;

public record InitialWhere(
    InitialExpression lhs,
    InitialExpression rhs
) implements InitialExpression {
}
