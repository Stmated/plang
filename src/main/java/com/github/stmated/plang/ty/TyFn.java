package com.github.stmated.plang.ty;

import java.util.Arrays;

public record TyFn(
  TyParam[] parameters,
  boolean vararg,
  Ty returnTy
) implements Ty {

  @Override
  public String toString() {

    final var parameterStrings = Arrays.stream(parameters()).map(TyParam::toString).toList();
    final var parametersString = String.join(", ", parameterStrings);
    final var returnString = returnTy().toShortString();
    return STR."\{returnString}(\{parametersString}\{vararg ? ", ..." : ""})";
  }
}
