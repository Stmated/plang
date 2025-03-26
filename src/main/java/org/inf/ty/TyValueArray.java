package org.inf.ty;


import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public record TyValueArray(
  @Nonnull
  Ty elementType,
  @Nullable
  Integer size
) implements TyValue {

  @Override
  public TyValueKind getValueKind() {
    return TyValueKind.ARRAY;
  }

  @Override
  public String toString() {
    return "[" + elementType().toShortString() + ";" + size() + "]";
  }
}
