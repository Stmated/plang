package com.github.stmated.plang.ipr;

public record InitialImportPathAlias(
    InitialIdentifier alias,
    InitialImportCapable target
) implements InitialExpression, InitialImportCapable {
}
