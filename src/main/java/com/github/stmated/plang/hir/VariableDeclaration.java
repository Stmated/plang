package com.github.stmated.plang.hir;

public record VariableDeclaration(Identifier identifier, MutabilityKind mutabilityKind, Type type) implements Expression {

  @Override
  public Type getResultType() {
    return this.type();
  }
}
