package com.github.stmated.plang.hir.model;

public record HirPath(HirExpression owner, HirExpression member) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//    return this.member().getResultType();
//  }
}
