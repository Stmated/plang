package com.github.stmated.plang.ipr;

import com.github.stmated.plang.hir.MutabilityKind;

public record InitialRef(InitialVariableDeclaration variableDeclaration) implements InitialExpression {
}
