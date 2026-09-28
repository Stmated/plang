package org.inf.ty;

import java.util.Arrays;

public record TyStruct(
  TyField[] fields
) implements Ty {

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
    return "struct {" + Arrays.toString(fields) + "}";
  }
}
