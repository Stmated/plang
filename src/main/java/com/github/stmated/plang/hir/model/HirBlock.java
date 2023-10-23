package com.github.stmated.plang.hir.model;

public record HirBlock(HirExpression[] children) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//
//    // TODO: Needs to be dealt with in some other way. Maybe give back a composition type that is compressed later?
//    if (children.length > 0) {
//      return children[0].getResultType();
//    }
//
//    return null;
//  }
}
