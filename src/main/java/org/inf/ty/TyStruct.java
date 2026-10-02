package org.inf.ty;

import java.util.Arrays;

public record TyStruct(
  TyField[] fields
) implements Ty {

  public boolean hasUnnamedFields() {
    return Arrays.stream(fields).anyMatch(field -> field.name() == null);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof TyStruct struct && Arrays.equals(fields, struct.fields);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(fields);
  }

  @Override
  public String toString() {
    if (hasUnnamedFields()) {
      return "(" + String.join(", ", Arrays.stream(fields).map(TyField::toString).toList())
        + (fields.length == 1 ? "," : "") + ")";
    }
    return "struct {" + Arrays.toString(fields) + "}";
  }
}
