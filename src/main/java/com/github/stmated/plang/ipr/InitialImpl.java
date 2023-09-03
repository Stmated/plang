package com.github.stmated.plang.ipr;

public record InitialImpl(
    InitialIdentifier traitIdentifier,
    InitialExpression forExpression,
    InitialBlock block
) implements InitialExpression {
}
