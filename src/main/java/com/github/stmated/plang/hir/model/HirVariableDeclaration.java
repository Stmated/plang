package com.github.stmated.plang.hir.model;

public record HirVariableDeclaration(
  HirIdentifier identifier,
  HirMutabilityKind mutabilityKind,
  HirExpression type
) implements HirExpression {

  @Override
  public String toString() {
    return STR."\{mutabilityKind} \{identifier}:\{type}";
  }
}
