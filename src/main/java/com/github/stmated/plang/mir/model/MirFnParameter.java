package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;

public record MirFnParameter(
  String name,
  Ty ty
) {

  @Override
  public String toString() {
    return name + ":" + ty.toShortString();
  }
}
