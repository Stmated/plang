package com.github.stmated.plang.hir.model;

import java.util.Arrays;

public record HirPath(HirExpression[] elements) implements HirExpression {

  @Override
  public String toString() {
    return Arrays.toString(elements);
  }
}
