package org.inf.ty;

import java.util.EnumSet;

public interface TyValueNumber extends TyValue {

  byte radix();

  BitWidth width();

  boolean signed();

  EnumSet<TyFlags> flags();

  @Override
  default boolean isNumber() {
    return true;
  }
}
