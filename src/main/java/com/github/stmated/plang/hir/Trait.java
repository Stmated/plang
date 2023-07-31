package com.github.stmated.plang.hir;

public record Trait(Expression[] children) implements Expression {

  @Override
  public Type getResultType() {

    // TODO: Needs to implement the type of "trait" -- should be very generic
    return null;
  }
}
