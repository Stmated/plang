package org.inf.ty;

import java.util.Arrays;

/// `tuple` preserves source syntax for access checks; it does not affect structural compatibility.
public record TyStruct(
  TyField[] fields,
  boolean tuple
) implements Ty {

  public TyStruct(final TyField[] fields) {
    this(fields, Arrays.stream(fields).anyMatch(field -> field.name() == null));
  }

  public boolean hasUnnamedFields() {
    return Arrays.stream(fields).anyMatch(field -> field.name() == null);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof TyStruct struct && tuple == struct.tuple && Arrays.equals(fields, struct.fields);
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(fields) + Boolean.hashCode(tuple);
  }

  @Override
  public String toString() {
    if (tuple) {
      return "(" + String.join(", ", Arrays.stream(fields).map(TyField::toString).toList())
        + (fields.length == 1 ? "," : "") + ")";
    }
    return "struct {" + Arrays.toString(fields) + "}";
  }
}
