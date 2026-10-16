package org.inf.ty;

public record TyParam(
  String name,
  Ty ty
) {

  @Override
  public String toString() {
    if (ty == null) {
      return name;
    }

    return "%s:%s".formatted(name, ty.toShortString());
  }
}
