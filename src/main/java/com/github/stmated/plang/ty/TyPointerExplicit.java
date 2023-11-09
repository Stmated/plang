package com.github.stmated.plang.ty;

/**
 * Should be avoided at all costs and should only be used when working with external dependencies such as C/C++ code.
 */
public record TyPointerExplicit(
  Ty inner,
  TyPointerAddressSpace addressSpace
) implements Ty {

  public TyPointerExplicit(Ty inner) {
    this(inner, TyPointerAddressSpace.CPU);
  }
}
