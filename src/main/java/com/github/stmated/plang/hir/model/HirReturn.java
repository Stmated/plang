package com.github.stmated.plang.hir.model;

public record HirReturn(HirExpression expression) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//    return this.expression().getResultType();
//  }
}
