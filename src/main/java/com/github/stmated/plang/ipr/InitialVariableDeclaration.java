package com.github.stmated.plang.ipr;

import com.github.stmated.plang.hir.MutabilityKind;
import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialVariableDeclaration(
  InitialIdentifier identifier,
  MutabilityKind mutabilityKind,
  InitialExpression type,
  boolean ref
) implements InitialExpression {

  @Override
  public String toString() {
    return mutabilityKind
      + " "
      + (ref ? "ref " : "")
      + identifier
      + ((type != null) ? (": " + type) : "");
  }

  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitVariableDeclaration(this);
  }
}
