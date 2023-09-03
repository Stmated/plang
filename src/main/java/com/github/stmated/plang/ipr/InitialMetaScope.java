package com.github.stmated.plang.ipr;

public record InitialMetaScope(
    InitialExpression lhs,
    InitialExpression rhs
) implements InitialExpression {
}
