package com.github.stmated.plang.hir.model;

import java.util.Arrays;

public record HirStruct(
  HirVariableDeclaration[] declarations
) implements HirExpression {

  @Override
  public String toString() {
    return STR."struct {\{Arrays.toString(declarations)}}";
  }
}
