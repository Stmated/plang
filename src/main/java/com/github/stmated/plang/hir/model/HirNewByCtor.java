package com.github.stmated.plang.hir.model;

public record HirNewByCtor(
  HirExpression target,
  HirIdentifier allocator,
  HirExpression arguments
) implements HirExpression {

  @Override
  public String toString() {
    return STR."new \{target}(\{arguments})";
  }
}
