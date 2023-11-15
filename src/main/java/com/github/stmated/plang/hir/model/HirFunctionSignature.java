package com.github.stmated.plang.hir.model;

import java.util.Arrays;
import org.codehaus.commons.nullanalysis.NotNull;
import org.codehaus.commons.nullanalysis.Nullable;

public record HirFunctionSignature(
  @NotNull
  HirParameter[] parameters,
  boolean vararg,
  @Nullable
  HirExpression returnType
) implements HirExpression {

  @Override
  public String toString() {
    
    final var parameterStrings = Arrays.stream(parameters()).map(Record::toString).toList();
    return STR."(\{String.join(", ", parameterStrings)}\{vararg() ? "..." : ""}): \{returnType}";
  }
}
