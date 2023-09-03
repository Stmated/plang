package com.github.stmated.plang.ipr;

public record InitialForEach(
    InitialExpression source,
    InitialVariableDeclaration item,
    InitialExpression body
) implements InitialExpression {
}
