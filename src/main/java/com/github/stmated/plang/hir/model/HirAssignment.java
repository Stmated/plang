package com.github.stmated.plang.hir.model;

/**
 * TODO: Should this just be a general binary operation, or is it actually significant?
 *
 * @param lhs
 * @param rhs
 */
public record HirAssignment(HirExpression lhs, HirExpression rhs) implements HirExpression {

  @Override
  public String toString() {
    return STR."\{lhs} = \{rhs}";
  }
}
