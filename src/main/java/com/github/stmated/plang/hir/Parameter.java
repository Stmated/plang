package com.github.stmated.plang.hir;

public record Parameter(Identifier identifier, Type type) implements Expression {

  @Override
  public Type getResultType() {
    return this.type();
  }
}
