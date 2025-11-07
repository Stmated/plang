package org.inf.ty;

import lombok.Builder;

import java.util.Arrays;

@Builder(toBuilder = true)
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
    return "(" + parametersString + (vararg ? ", ..." : "") + "): " + returnString;
  }
}
