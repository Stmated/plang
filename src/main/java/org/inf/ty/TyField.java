package org.inf.ty;

public record TyField(String name, Ty ty) {

  @Override
  public String toString() {
    if (name == null) {
      return ty.toString();
    }

    return "%s: %s".formatted(name, ty);
  }
}
