package com.github.stmated.plang.hir.model;

import java.util.Arrays;

public record HirArray(
  HirExpression[] elements,
  HirExpression elementType,
  HirExpression length
) implements HirExpression {

  @Override
  public String toString() {

    final var childrenStrings = Arrays.stream(elements()).map(Object::toString).toList();
    final var childrenString = String.join(", ", childrenStrings);

    return STR."[\{childrenString};\{elementType()};\{length()}]";
  }
}
