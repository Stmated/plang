package com.github.stmated.plang.ty;


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
    return STR."[\{elementType().toShortString()};\{size()}]";
  }
}
