package org.inf.ty;

public record TyOpaque() implements Ty {

  @Override
  public String toString() {
    return "Opaque";
  }
}
