package com.github.stmated.plang.hir;

public record Call(FunctionReference functionReference, Argument[] arguments, boolean partial) implements Expression {

  @Override
  public Type getResultType() {

    // TODO: This is not true if it's a partial call, then it's a function reference with less parameters
    return this.functionReference().function().getResultType();
  }
}
