package com.github.stmated.plang.ipr;

public record InitialLoopDoWhile(
    InitialExpression body,
    InitialExpression predicate
) implements InitialExpression {
}
