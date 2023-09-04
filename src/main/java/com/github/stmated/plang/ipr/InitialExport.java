package com.github.stmated.plang.ipr;

public record InitialExport(
    InitialExpression exported,
    boolean isDefault
) implements InitialExpression {
}
