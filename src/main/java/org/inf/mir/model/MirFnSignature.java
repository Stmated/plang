package org.inf.mir.model;

import jakarta.annotation.Nonnull;
import org.inf.ty.Ty;

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
    return "(" + String.join(", ", parameterStrings) + (vararg() ? ", ..." : "") + "): " + returnType;
  }
}
