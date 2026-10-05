package org.inf.ty.util;

import org.inf.ty.*;

import java.util.Objects;

/// Value-type equivalence without conversions or temporary types.
public final class TypeComparison {

  private TypeComparison() {
  }

  /// Ignores numeric width explicitness, function parameter names, and aggregate source syntax.
  /// Aggregate labels, slot order, numeric flags, and all other value-type properties must match.
  public static boolean sameValueType(Ty actual, Ty expected) {
    if (actual == expected) {
      return true;
    }
    if (actual instanceof TyValueNumber a && expected instanceof TyValueNumber b) {
      return sameNumericValueType(a, b);
    }
    if (actual instanceof TyStruct a && expected instanceof TyStruct b) {
      if (a.fields().length != b.fields().length) {
        return false;
      }
      for (var i = 0; i < a.fields().length; i++) {
        if (!Objects.equals(a.fields()[i].name(), b.fields()[i].name())
          || !sameValueType(a.fields()[i].ty(), b.fields()[i].ty())) {
          return false;
        }
      }
      return true;
    }
    if (actual instanceof TyValueArray a && expected instanceof TyValueArray b) {
      return Objects.equals(a.size(), b.size()) && sameValueType(a.elementType(), b.elementType());
    }
    if (actual instanceof TyPointer<?> a && expected instanceof TyPointer<?> b) {
      return a.addressSpace() == b.addressSpace() && sameValueType(a.inner(), b.inner());
    }
    if (actual instanceof TyFn a && expected instanceof TyFn b) {
      if (a.vararg() != b.vararg() || a.parameters().length != b.parameters().length
        || !sameValueType(a.returnTy(), b.returnTy())) {
        return false;
      }
      for (var i = 0; i < a.parameters().length; i++) {
        if (!sameValueType(a.parameters()[i].ty(), b.parameters()[i].ty())) {
          return false;
        }
      }
      return true;
    }
    if (actual instanceof TyUnion a && expected instanceof TyUnion b) {
      if (a.types().length != b.types().length) {
        return false;
      }
      for (var i = 0; i < a.types().length; i++) {
        if (!sameValueType(a.types()[i], b.types()[i])) {
          return false;
        }
      }
      return true;
    }
    return Objects.equals(actual, expected);
  }

  private static boolean sameNumericValueType(TyValueNumber a, TyValueNumber b) {
    if (!sameBitWidth(a, b) || !sameSignedness(a, b) || !sameRadix(a, b) || !a.flags().equals(b.flags())) {
      return false;
    }
    return switch (a) {
      case TyValueNumberInteger _ -> b instanceof TyValueNumberInteger;
      case TyValueNumberPrecisioned real -> b instanceof TyValueNumberPrecisioned other
        && real.kind() == other.kind() && samePrecision(real, other);
      case TyValueNumberScaled scaled -> b instanceof TyValueNumberScaled other && scaled.scale() == other.scale();
      default -> false;
    };
  }

  static boolean sameBitWidth(TyValueNumber a, TyValueNumber b) {
    return a.width().value() == b.width().value();
  }

  static boolean sameWidthExplicitness(TyValueNumber a, TyValueNumber b) {
    return a.width().explicit() == b.width().explicit();
  }

  static boolean sameSignedness(TyValueNumber a, TyValueNumber b) {
    return a.signed() == b.signed();
  }

  static boolean sameRadix(TyValueNumber a, TyValueNumber b) {
    return a.radix() == b.radix();
  }

  static boolean samePrecision(TyValueNumberPrecisioned a, TyValueNumberPrecisioned b) {
    return a.precision() == b.precision();
  }
}
