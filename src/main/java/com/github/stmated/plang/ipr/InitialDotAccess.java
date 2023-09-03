package com.github.stmated.plang.ipr;

public record InitialDotAccess(
    InitialExpression lhs,
    InitialExpression rhs
) implements InitialExpression {
}
