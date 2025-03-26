package org.inf.mir.model;

import org.inf.ty.Ty;

public record MirFnParameter(
  String name,
  Ty ty
) {

  @Override
  public String toString() {
    return name + ":" + ty.toShortString();
  }
}
