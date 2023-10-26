package com.github.stmated.plang.hir.model;

public record HirIdentifier(String name) implements HirExpression {

  @Override
  public String toString() {
    return name;
  }
}
