package com.github.stmated.plang.hir.model;

public record HirTuple(HirTupleKeyValue[] children) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//
//    // TODO: Needs to implement the kind of "trait" -- should be very generic
//    return null;
//  }
}
