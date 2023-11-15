package com.github.stmated.plang.hir.model;

import org.codehaus.commons.nullanalysis.NotNull;

public record HirParameter(
  @NotNull
  HirExpression identifier,
  @NotNull
  HirExpression type,
  boolean vararg
) implements HirExpression {

  @Override
  public String toString() {
    return STR."\{identifier}:\{type}";
  }
}
