package com.github.stmated.plang.ipr;

public record InitialConditional(
    InitialExpression predicate,
    InitialExpression pass,
    InitialExpression fail
) implements InitialExpression {
}
