package com.github.stmated.plang.ipr;

public record InitialImportPath(
    InitialImportCapable lhs,
    InitialImportCapable rhs
) implements InitialExpression, InitialImportCapable {
}
