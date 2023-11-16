package com.github.stmated.plang.hir.model;

public record HirArrayAccess(
  HirExpression target,
  HirExpression accessor
) implements HirExpression {

  @Override
  public String toString() {
    return STR."\{target}[\{accessor}]";
  }
}
