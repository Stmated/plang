package org.inf.hir;

import org.inf.ty.TyStruct;
import org.inf.ty.TyValueNumberInteger;
import org.inf.util.IntegerLiterals;

import java.math.BigInteger;

/// Resolve positional slots before lowering them to aggregate field places.
public final class HirTupleAccess {

  private HirTupleAccess() {
  }

  /// Returns null when index validation must wait for final type resolution.
  public static Integer availableIndex(final TyStruct tuple, final Hir.Expression accessor) {
    if (!(accessor.valueTy() instanceof TyValueNumberInteger type) || !(accessor instanceof Hir.Literal literal)) {
      return null;
    }
    final var index = IntegerLiterals.parse(literal.content(), type.radix());
    if (index.signum() < 0 || index.compareTo(BigInteger.valueOf(tuple.fields().length)) >= 0) {
      return null;
    }
    return index.intValueExact();
  }

  public static int index(TyStruct tuple, Hir.Expression accessor) {
    if (!(accessor.valueTy() instanceof TyValueNumberInteger type)) {
      throw new IllegalArgumentException("Tuple index must be an integer literal");
    }
    if (!(accessor instanceof Hir.Literal literal)) {
      throw new IllegalArgumentException("Computed tuple indices are not supported yet; use an integer literal");
    }
    final var index = IntegerLiterals.parse(literal.content(), type.radix());
    if (index.signum() < 0) {
      throw new IllegalArgumentException("Tuple index must not be negative: " + index);
    }
    if (index.compareTo(BigInteger.valueOf(tuple.fields().length)) >= 0) {
      throw new IllegalArgumentException("Tuple index out of range: %s  sfor %d slots".formatted(index, tuple.fields().length));
    }
    return index.intValueExact();
  }
}
