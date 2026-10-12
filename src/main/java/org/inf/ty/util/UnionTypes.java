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

  public static boolean compatible(final Ty actualType, final Ty expectedType) {
    final var actual = Tys.completionTy(actualType);
    final var expected = Tys.completionTy(expectedType);
    if (Tys.isDeadend(actual) || TypeComparison.same(actual, expected)) {
      return true;
    }
    final var source = Tys.asCompletion(actual, TyUnion.class);
    if (source != null) {
      return Tys.asCompletion(expected, TyUnion.class) != null
        && ArrayUtils.none(source.types(), type -> !compatible(type, expected));
    }

    final var destination = Tys.asCompletion(expected, TyUnion.class);
    return destination != null && memberIndex(actual, destination) >= 0;
  }

  public static int memberIndex(final Ty actualType, final TyUnion expected) {
    final var actual = Tys.completionTy(actualType);
    for (var i = 0; i < expected.types().length; i++) {
      if (memberCompatible(actual, expected.types()[i])) {
        return i;
      }
    }
    return -1;
  }

  private static boolean memberCompatible(final Ty actual, final Ty expected) {
    if (TypeComparison.same(actual, expected)) {
      return true;
    }
    if (actual instanceof TyValueArray source && expected instanceof TyValueArray destination) {
      return (destination.size() == null || destination.size().equals(source.size()))
        && TypeComparison.same(source.elementType(), destination.elementType());
    }
    return actual instanceof TyPointer<?> source && expected instanceof TyPointer<?> destination
      && source.addressSpace() == destination.addressSpace() && memberCompatible(source.inner(), destination.inner());
  }
}
