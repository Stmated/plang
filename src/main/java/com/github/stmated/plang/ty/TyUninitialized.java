package com.github.stmated.plang.ty;

public record TyUninitialized<T extends Ty>(
  T inner
) implements Ty {

  @Override
  public String toString() {
    return STR."Uninitialized \{this.inner().toShortString()}";
  }
}
