package org.inf.ty.util;

import org.inf.ty.*;

import java.util.Arrays;

/// Tuple detection and element validity, independent of type comparison and conversion.
public final class TupleTypes {

  private TupleTypes() {
  }

  public static boolean containsTuple(Ty ty) {
    return switch (ty) {
      case TyStruct struct -> struct.hasUnnamedFields()
        || Arrays.stream(struct.fields()).anyMatch(field -> containsTuple(field.ty()));
      case TyValueArray array -> containsTuple(array.elementType());
      case TyPointer<?> pointer -> containsTuple(pointer.inner());
      case TyFn fn -> containsTuple(fn.returnTy())
        || Arrays.stream(fn.parameters()).anyMatch(parameter -> containsTuple(parameter.ty()));
      case TyUnion union -> Arrays.stream(union.types()).anyMatch(TupleTypes::containsTuple);
      case null, default -> false;
    };
  }

  public static void requireElementType(Ty ty) {
    if (!isElementType(ty)) {
      throw new IllegalArgumentException("Invalid tuple element type: " + ty);
    }
  }

  private static boolean isElementType(Ty ty) {
    return switch (ty) {
      case TyValueNumber number -> number.width().value() > 0;
      case TyValueBoolean _, TyValueString _ -> true;
      case TyStruct struct -> Arrays.stream(struct.fields()).allMatch(field -> isElementType(field.ty()));
      case TyValueArray array -> isElementType(array.elementType()) && (array.size() == null || array.size() >= 0);
      case TyPointer<?> pointer -> pointer.inner() instanceof TyOpaque || isElementType(pointer.inner());
      case TyUnion union -> Arrays.stream(union.types()).allMatch(type -> type == Ty.VOID || isElementType(type));
      case TyFn fn -> (fn.returnTy() == Ty.VOID || fn.returnTy() == Ty.DEADEND || isElementType(fn.returnTy()))
        && Arrays.stream(fn.parameters()).allMatch(parameter -> isElementType(parameter.ty()));
      case null, default -> false;
    };
  }
}
