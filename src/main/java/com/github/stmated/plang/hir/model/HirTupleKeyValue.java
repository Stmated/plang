package com.github.stmated.plang.hir.model;

import org.codehaus.commons.nullanalysis.NotNull;
import org.codehaus.commons.nullanalysis.Nullable;

public record HirTupleKeyValue(
  @Nullable
  HirIdentifier key,
  @NotNull
  HirExpression value
) implements HirExpression {


}
