package com.github.stmated.plang.ty;

public record TyUninitialized<T extends Ty>(
  T inner
) implements Ty {

  @Override
  public String toString() {
    return "Uninitialized " + this.inner().toShortString();
  }
}
