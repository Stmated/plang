package com.github.stmated.plang.ipr;

import com.github.stmated.plang.hir.MutabilityKind;

public record InitialVariableDeclaration(
    InitialIdentifier identifier,
    MutabilityKind mutabilityKind,
    InitialExpression type,
    boolean ref
) implements InitialExpression {
}
