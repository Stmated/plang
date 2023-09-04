package com.github.stmated.plang.ipr;

public record InitialImport(
    InitialImportCapable path
) implements InitialExpression {
}
