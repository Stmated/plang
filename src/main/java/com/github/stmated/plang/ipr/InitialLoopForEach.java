package com.github.stmated.plang.ipr;

public record InitialLoopForEach(
    InitialExpression source,
    InitialVariableDeclaration item,
    InitialExpression body
) implements InitialExpression {
}
