package com.github.stmated.plang.ipr;

public record InitialMatch(
    InitialExpression target,
    InitialBlock block
) implements InitialExpression {
}
