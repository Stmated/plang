package com.github.stmated.plang.ty;

public interface TyValueNumber extends TyValue {

  byte radix();

  int width();

  boolean signed();

  @Override
  default boolean isNumber() {
    return true;
  }
}
