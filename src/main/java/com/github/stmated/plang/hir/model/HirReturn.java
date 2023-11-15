package com.github.stmated.plang.hir.model;

public record HirReturn(HirExpression expression) implements HirExpression {

  @Override
  public String toString() {
    return STR."return \{expression}";
  }
}
