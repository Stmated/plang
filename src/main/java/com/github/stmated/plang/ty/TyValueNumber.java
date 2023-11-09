package com.github.stmated.plang.ty;

import java.util.EnumSet;

public interface TyValueNumber extends TyValue {

  byte radix();

  int width();

  boolean signed();

  EnumSet<TyFlags> flags();

  @Override
  default boolean isNumber() {
    return true;
  }
}
