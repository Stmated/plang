package com.github.stmated.plang.ipr;

public record InitialNew(
    InitialExpression target,
    InitialBlock block
) implements InitialExpression {
}
