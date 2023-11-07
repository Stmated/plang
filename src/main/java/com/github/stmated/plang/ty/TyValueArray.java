package com.github.stmated.plang.ty;

public record TyValueArray(Ty elementType, int size) implements TyValue {

  @Override
  public TyValueKind getValueKind() {
    return TyValueKind.ARRAY;
  }
}
