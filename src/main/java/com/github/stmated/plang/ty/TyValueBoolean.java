package com.github.stmated.plang.ty;

public record TyValueBoolean() implements TyValue {

  @Override
  public TyValueKind getValueKind() {
    return TyValueKind.BOOLEAN;
  }
}
