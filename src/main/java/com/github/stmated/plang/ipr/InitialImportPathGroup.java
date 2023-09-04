package com.github.stmated.plang.ipr;

public record InitialImportPathGroup(
    InitialImportCapable[] items
) implements InitialExpression, InitialImportCapable {
}
