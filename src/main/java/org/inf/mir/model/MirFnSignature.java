package org.inf.mir.model;

import jakarta.annotation.Nonnull;
import org.inf.ty.Ty;

import java.util.Arrays;
import java.util.Objects;

/** The parameters array is shared and must not be mutated after construction. */
public record MirFnSignature(
  @Nonnull
  MirFnParameter[] parameters,
  boolean vararg,
  @Nonnull
  Ty returnType
) {

  public MirFnSignature {
    for (final var parameter : parameters) {
      Objects.requireNonNull(parameter);
    }
    Objects.requireNonNull(returnType);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof MirFnSignature signature
      && vararg == signature.vararg
      && Arrays.equals(parameters, signature.parameters)
      && returnType.equals(signature.returnType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(parameters), vararg, returnType);
  }

  @Override
  public String toString() {

    final var parameterStrings = Arrays.stream(parameters()).map(Record::toString).toList();
    return "(" + String.join(", ", parameterStrings) + (vararg() ? ", ..." : "") + "): " + returnType;
  }
}
