package com.github.stmated.plang.ty;

public interface TyValueNumber extends TyValue {

  int radix();

  int width();

  boolean signed();

  @Override
  default boolean isNumber() {
    return true;
  }
}
