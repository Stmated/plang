package com.github.stmated.plang.hir.model;

public record HirVariableDeclaration(HirIdentifier identifier, HirMutabilityKind mutabilityKind, HirExpression type) implements HirExpression {


}
