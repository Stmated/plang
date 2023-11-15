package com.github.stmated.plang.hir.model;

public record HirImpl(HirExpression[] children) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//
//    // TODO: Needs to implement the kind of "impl" -- should be very generic
//    return null;
//  }
}
