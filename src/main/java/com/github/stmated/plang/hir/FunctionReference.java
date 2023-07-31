package com.github.stmated.plang.hir;

public record FunctionReference(Function function) implements Expression {

  @Override
  public Type getResultType() {

    // TODO: Implement how to represent a type that is a reference to a function
    return null;
  }
}
