package com.github.stmated.plang.hir.model;

public record HirConditional(
    HirExpression predicate,
    HirExpression pass,
    HirExpression fail
) implements HirExpression {

  @Override
  public String toString() {
    return STR."if (\{this.predicate()}) then {\{this.pass()}} else {\{this.fail()}}";
  }
}
