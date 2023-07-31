package com.github.stmated.plang.hir;

public record VariableReference(VariableDeclaration declaration) implements Expression {

  @Override
  public Type getResultType() {
    return this.declaration().getResultType();
  }
}
