package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import jakarta.annotation.Nonnull;
import java.util.Arrays;

public record MirFnSignature(
  @Nonnull
  MirFnParameter[] parameters,
  boolean vararg,
  @Nonnull
  Ty returnType
) {

  @Override
  public String toString() {

    final var parameterStrings = Arrays.stream(parameters()).map(Record::toString).toList();
    return STR."(\{String.join(", ", parameterStrings)}\{vararg() ? ", ..." : ""}): \{returnType}";
  }
}
