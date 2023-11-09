package com.github.stmated.plang.ty;

/**
 * Used to say that "this value will be a pointer but should always be de-referenced upon use".
 * <p>
 * This is the regular type of safe pointer that should be used throughout the language.
 */
public record TyPointer<T extends Ty>(
  T inner,
  TyPointerAddressSpace addressSpace
) implements Ty {

  public TyPointer(T inner) {
    this(inner, TyPointerAddressSpace.CPU);
  }
}
