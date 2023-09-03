package com.github.stmated.plang.hir;

public record LoopForEach(
    Expression source,
    VariableDeclaration item,
    Expression body
) implements Loop {

  @Override
  public Type getResultType() {
    return this.body().getResultType();
  }
}
