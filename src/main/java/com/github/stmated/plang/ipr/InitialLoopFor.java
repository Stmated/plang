package com.github.stmated.plang.ipr;

public record InitialLoopFor(
    InitialAssignment[] assignments,
    InitialExpression predicate,
    InitialExpression[] steppers,
    InitialBlock block
) implements InitialExpression {
}
