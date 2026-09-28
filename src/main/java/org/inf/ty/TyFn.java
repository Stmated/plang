package org.inf.ty;

import lombok.Builder;

import java.util.Arrays;
import java.util.Objects;

@Builder(toBuilder = true)
public record TyFn(
  TyParam[] parameters,
  boolean vararg,
  Ty returnTy
) implements Ty {

  @Override
  public boolean equals(Object other) {
    return other instanceof TyFn fn
      && vararg == fn.vararg
      && Arrays.equals(parameters, fn.parameters)
      && Objects.equals(returnTy, fn.returnTy);
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(parameters), vararg, returnTy);
  }

  @Override
  public String toString() {

    final var parameterStrings = Arrays.stream(parameters()).map(TyParam::toString).toList();
    final var parametersString = String.join(", ", parameterStrings);
    final var returnString = returnTy().toShortString();
    return "(" + parametersString + (vararg ? ", ..." : "") + "): " + returnString;
  }
}
