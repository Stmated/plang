package org.inf.hir.passes;

import jakarta.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyParam;
import org.inf.ty.util.Tys;

import java.util.Objects;

@UtilityClass
public class HirFnTyVisitorPass {

  @Nonnull
  public static TyFn fnToTyFn(Hir.FunctionSignature signature) {

    final var parameterTys = new TyParam[signature.parameters().length];
    for (var i = 0; i < signature.parameters().length; i++) {

      final var parameter = signature.parameters()[i];
      final var parameterName = parameter.lexeme().name();
      final var paramTy = parameter.resolvedTy();

      parameterTys[i] = new TyParam(parameterName, paramTy);
    }

    final var annotation = signature.returnTypeAnnotation();
    var returnTy = annotation == null ? null : annotation.ty();
    if (Tys.containsInferred(returnTy) && signature.ty() != null) {
      returnTy = signature.ty().returnTy();
    }
    return new TyFn(parameterTys, signature.vararg(), Objects.requireNonNullElse(returnTy, Ty.INFER));
  }
}
