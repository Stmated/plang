package com.github.stmated.plang.ipr;

public record InitialCollectionItem(
    InitialExpression lhs,
    InitialExpression rhs
) implements InitialExpression {
}
