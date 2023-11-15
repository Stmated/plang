package com.github.stmated.plang.hir.model;

public record HirCall(
  HirExpression target,
  HirArgument[] arguments,
  boolean partial
) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//
//    // TODO: This is not true if it's a partial call, then it's a function reference with less parameters
//    return this.functionReference().function().getResultType();
//  }
}
