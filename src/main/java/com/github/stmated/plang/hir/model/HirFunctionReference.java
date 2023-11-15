package com.github.stmated.plang.hir.model;

public record HirFunctionReference(HirFunction function) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//
//    // TODO: Implement how to represent a kind that is a reference to a function
//    return null;
//  }
}
