package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.ty.TyFn;
import org.inf.ty.util.Tys;

/// Supplies missing lambda parameter types from an expected function signature.
@UtilityClass
final class HirFunctionTyping {

  static void contextualize(final Hir.FunctionSignature signature, final TyFn expected) {
    final var parameters = signature.parameters();
    var changed = false;
    for (var i = 0; i < Math.min(parameters.length, expected.parameters().length); i++) {
      final var parameter = parameters[i];
      final var actual = parameter.valueType().ty();
      final var destination = expected.parameters()[i].ty();
      if (Tys.isInferred(actual) && !Tys.isInferred(destination)) {
        parameter.valueType(new Hir.TyExpr(destination));
        changed = true;
      }
    }
    if (changed) {
      signature.ty(null);
    }
  }
}
