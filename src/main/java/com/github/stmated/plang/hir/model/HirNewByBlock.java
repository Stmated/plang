package com.github.stmated.plang.hir.model;

import java.util.Arrays;

public record HirNewByBlock(
  HirExpression target,
  HirIdentifier allocator,
  HirAssignment[] fields
) implements HirExpression {

  @Override
  public String toString() {
    return STR."new \{target}{\{Arrays.toString(fields)}}";
  }
}
