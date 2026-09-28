package org.inf.mir;

import org.inf.mir.model.MirFnParameter;
import org.inf.mir.model.MirFnSignature;
import org.inf.ty.*;
import org.inf.ty.util.Tys;

import java.util.Arrays;

public final class MirTypes {

  private MirTypes() {
  }

  /** Current source aggregates have reference semantics; their pointee retains the aggregate layout type. */
  public static Ty valueType(Ty type) {
    return switch (type) {
      case TyStruct struct -> new TyPointer<>(struct);
      case TyValueArray array -> new TyPointer<>(array);
      case TyFn fn -> new TyPointer<>(functionType(signature(fn)));
      case TyUnion union -> Tys.union(Arrays.stream(union.types()).map(MirTypes::valueType).toArray(Ty[]::new));
      default -> type;
    };
  }

  public static MirFnSignature signature(TyFn fn) {
    final var parameters = Arrays.stream(fn.parameters())
      .map(p -> new MirFnParameter(p.name(), valueType(p.ty())))
      .toArray(MirFnParameter[]::new);
    return new MirFnSignature(parameters, fn.vararg(), returnType(fn.returnTy()));
  }

  public static Ty returnType(Ty type) {
    return type == Ty.DEADEND ? Ty.VOID : valueType(type);
  }

  public static TyFn functionType(MirFnSignature signature) {
    final var parameters = Arrays.stream(signature.parameters())
      .map(p -> new TyParam(p.name(), p.ty()))
      .toArray(TyParam[]::new);
    return new TyFn(parameters, signature.vararg(), signature.returnType());
  }

  public static Ty pointee(Ty type) {
    if (type instanceof TyPointer<?> pointer) {
      return pointer.inner();
    }
    throw new IllegalArgumentException("Expected reference type, got " + type);
  }
}
