package org.inf.ty.util;

import lombok.experimental.UtilityClass;
import org.inf.ty.Ty;
import org.inf.ty.TyUnion;
import org.inf.ty.TyPointer;
import org.inf.ty.TyValueArray;
import org.inf.util.ArrayUtils;

/// Union injection and widening preserve each source member's value type.
@UtilityClass
public class UnionTypes {

  public static boolean compatible(final Ty actual, final Ty expected) {
    if (actual == Ty.DEADEND || TypeComparison.sameValueType(actual, expected)) {
      return true;
    }
    if (actual instanceof TyUnion source) {
      return expected instanceof TyUnion
        && ArrayUtils.none(source.types(), type -> !compatible(type, expected));
    }
    return expected instanceof TyUnion destination && memberIndex(actual, destination) >= 0;
  }

  public static int memberIndex(final Ty actual, final TyUnion expected) {
    for (var i = 0; i < expected.types().length; i++) {
      if (memberCompatible(actual, expected.types()[i])) {
        return i;
      }
    }
    return -1;
  }

  private static boolean memberCompatible(final Ty actual, final Ty expected) {
    if (TypeComparison.sameValueType(actual, expected)) {
      return true;
    }
    if (actual instanceof TyValueArray source && expected instanceof TyValueArray destination) {
      return (destination.size() == null || destination.size().equals(source.size()))
        && TypeComparison.sameValueType(source.elementType(), destination.elementType());
    }
    return actual instanceof TyPointer<?> source && expected instanceof TyPointer<?> destination
      && source.addressSpace() == destination.addressSpace() && memberCompatible(source.inner(), destination.inner());
  }
}
