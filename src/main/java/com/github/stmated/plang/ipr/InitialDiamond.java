package com.github.stmated.plang.ipr;

public record InitialDiamond(
    InitialIdentifier identifier,
    InitialExpression[] children
) implements InitialExpression {
}
