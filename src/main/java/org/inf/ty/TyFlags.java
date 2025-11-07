package org.inf.ty;

public enum TyFlags {

  CONSTANT(1),
  IMMUTABLE(2),
  MUTABLE(4);

  private final int value;

  TyFlags(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
