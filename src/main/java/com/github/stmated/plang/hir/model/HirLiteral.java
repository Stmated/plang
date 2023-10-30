package com.github.stmated.plang.hir.model;

import java.util.Objects;

public record HirLiteral(Object literal) implements HirExpression {

  public HirLiteral {

    if (literal instanceof HirExpression) {
      throw new IllegalArgumentException(STR."Not allowed to have an expression as a literal: \{literal}");
    }
  }

  @Override
  public String toString() {
    return Objects.toString(literal);
  }
}
