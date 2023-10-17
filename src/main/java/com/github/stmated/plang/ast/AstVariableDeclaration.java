package com.github.stmated.plang.ast;

import com.github.stmated.plang.hir.MutabilityKind;
import com.github.stmated.plang.ast.visitor.AstVisitor;

public record AstVariableDeclaration(
  AstIdentifier identifier,
  MutabilityKind mutabilityKind,
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
