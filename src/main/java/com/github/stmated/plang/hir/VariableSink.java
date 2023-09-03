package com.github.stmated.plang.hir;

public record VariableSink() implements Expression {

  @Override
  public Type getResultType() {
    return new Type(new Identifier("_"));
  }
}
