package org.inf.ty;

/**
 * A reference to storage in the given address space. In MIR this is a value type,
 * not a request for an implicit load; loads and field/element access are explicit.
 */
public record TyPointer<T extends Ty>(
  T inner,
  TyPointerAddressSpace addressSpace
) implements Ty {

  public TyPointer(T inner) {
    this(inner, TyPointerAddressSpace.CPU);
  }

  @Override
  public String toString() {
    return this.inner().toShortString() + "*";
  }
}
