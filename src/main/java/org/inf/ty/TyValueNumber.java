package org.inf.ty;

import java.util.EnumSet;
import java.util.Set;

public interface TyValueNumber extends TyValue {

  byte radix();

  BitWidth width();

  boolean signed();

  Set<TyFlags> flags();

  @Override
  default boolean isNumber() {
    return true;
  }
}
