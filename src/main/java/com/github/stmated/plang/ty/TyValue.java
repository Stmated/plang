package com.github.stmated.plang.ty;

public interface TyValue extends Ty {

  TyValueKind getValueKind();

  default boolean isConstant() {
    return false;
  }

  default boolean isImmutable() {
    return false;
  }

  default boolean isNumber() {
    return false;
  }
}
