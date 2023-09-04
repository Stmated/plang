package com.github.stmated.plang.ipr;

public record InitialImportPathIdentifier(
    InitialIdentifier identifier
) implements InitialExpression, InitialImportCapable {
}
