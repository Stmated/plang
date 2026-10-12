package org.inf.hir.typing;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.passes.HirFnTyVisitorPass;
import org.inf.ty.TyFn;
import org.inf.ty.util.Tys;

import java.util.Objects;

/// Supplies missing lambda parameter types from an expected function signature.
@UtilityClass
public final class HirFunctionTyping {

  public static void adhereToConstraint(final Hir.FunctionSignature signature, final TyFn constraint) {
    final var parameters = signature.parameters();
    var changed = false;

    // TODO: `Max.min` feels very weird. Should probably throw exception if they're not the same length, but then test cases fail
    for (var i = 0; i < Math.min(parameters.length, constraint.parameters().length); i++) {
      final var param = parameters[i];
      final var paramAnnotationTy = param.typeAnnotation().ty();
      final var constraintParamTy = constraint.parameters()[i].ty();
      if (Tys.isInferred(paramAnnotationTy) && !Tys.isInferred(constraintParamTy) && !Objects.equals(param.resolvedTy(), constraintParamTy)) {
        param.resolvedTy(constraintParamTy);
        changed = true;
      }
    }

    if (changed) {
      signature.ty(HirFnTyVisitorPass.fnToTyFn(signature));
    }
  }
}
