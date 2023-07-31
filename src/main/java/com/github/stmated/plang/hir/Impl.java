package com.github.stmated.plang.hir;

public record Impl(Expression[] children) implements Expression {

  @Override
  public Type getResultType() {

    // TODO: Needs to implement the type of "impl" -- should be very generic
    return null;
  }
}
