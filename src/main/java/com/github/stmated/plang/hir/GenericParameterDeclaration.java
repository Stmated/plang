package com.github.stmated.plang.hir;

public record GenericParameterDeclaration(Identifier identifier, Type lowerBound, Type higherBound) implements Expression {

  @Override
  public Type getResultType() {

    if (this.lowerBound == null) {
      return Type.TYPE_UNKNOWN;
    }

    return this.lowerBound;
  }
}
