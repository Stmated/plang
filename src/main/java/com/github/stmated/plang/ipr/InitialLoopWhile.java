package com.github.stmated.plang.ipr;

public record InitialLoopWhile(
    InitialExpression predicate,
    InitialExpression body
) implements InitialExpression {
}
