package com.github.stmated.plang.ty;

import java.util.Arrays;

public record TyStruct(
  TyField[] fields
) implements Ty {

  @Override
  public String toString() {
    return "struct {" + Arrays.toString(fields) + "}";
  }
}
