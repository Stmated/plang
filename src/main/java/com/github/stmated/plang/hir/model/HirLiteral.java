package com.github.stmated.plang.hir.model;

public record HirLiteral(Object literal) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//    return HirType.fromJavaType(this.literal());
//  }
}
