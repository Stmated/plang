package com.github.stmated.plang.hir.model;

import java.util.Arrays;
import java.util.Objects;

public record HirExpressions(HirExpression[] children) implements HirExpression {

  @Override
  public String toString() {
    final var childStrings = String.join("; ", Arrays.stream(children()).map(Objects::toString).toList());
    return STR."[\{childStrings}]";
  }
}
