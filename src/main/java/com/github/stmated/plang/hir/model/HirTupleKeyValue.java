package com.github.stmated.plang.hir.model;

public record HirTupleKeyValue(HirIdentifier key, HirExpression value) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//
//    // TODO: Needs to implement the kind of "trait" -- should be very generic
//    return null;
//  }
}
