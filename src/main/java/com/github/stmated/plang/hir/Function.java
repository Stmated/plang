package com.github.stmated.plang.hir;

public record Function(Identifier identifier, Parameter[] parameters, Type returnType) implements Expression {

  @Override
  public Type getResultType() {
    return this.returnType();
  }
}
