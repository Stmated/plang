package com.github.stmated.plang.ty;

public record TyParam(
  String name,
  Ty ty
) {

  @Override
  public String toString() {

    return name + ":" + ty.toShortString();
  }
}
