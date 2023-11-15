package com.github.stmated.plang.hir.model;

public record HirLabeling(
  HirExpression lhs,
  HirExpression rhs
) implements HirExpression {

//  @Override
//  public String toString() {
//    return name;
//  }
}
