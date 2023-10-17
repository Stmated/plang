package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstVariableDeclaration(
  AstIdentifier identifier,
  AstMutabilityKind mutabilityKind,
  AstExpression type,
  boolean ref
) implements AstExpression {

  @Override
  public String toString() {
    return mutabilityKind
      + " "
      + (ref ? "ref " : "")
      + identifier
      + ((type != null) ? (": " + type) : "");
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitVariableDeclaration(this);
  }
}
