package com.github.stmated.plang.hir.model;

import org.codehaus.commons.nullanalysis.NotNull;
import org.codehaus.commons.nullanalysis.Nullable;

public record HirArgument(
  @Nullable
  String label,
  @NotNull
  HirExpression value
) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//    return this.expression().getResultType();
//  }
}
