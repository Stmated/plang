package com.github.stmated.plang.hir;

public record New(Type type, Argument[] arguments) implements Expression {

  @Override
  public Type getResultType() {
    return this.type();
  }
}
