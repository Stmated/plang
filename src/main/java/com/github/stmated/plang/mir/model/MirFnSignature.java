package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import java.util.Arrays;
import org.codehaus.commons.nullanalysis.NotNull;

public record MirFnSignature(
  @NotNull
  MirFnParameter[] parameters,
  boolean vararg,
  @NotNull
  Ty returnType
) {

  @Override
  public String toString() {

    final var parameterStrings = Arrays.stream(parameters()).map(Record::toString).toList();
    return STR."(\{String.join(", ", parameterStrings)}\{vararg() ? "..." : ""}): \{returnType}";
  }
}
