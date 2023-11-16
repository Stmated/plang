package com.github.stmated.plang.ty;

import org.codehaus.commons.nullanalysis.NotNull;
import org.codehaus.commons.nullanalysis.Nullable;

public record TyValueArray(
  @NotNull
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
