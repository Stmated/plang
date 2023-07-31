package com.github.stmated.plang.hir;

public record GenericParameterReference(GenericParameterDeclaration declaration) implements Expression {

  @Override
  public Type getResultType() {
    return this.declaration().getResultType();
  }
}
