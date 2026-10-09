package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.ty.util.Tys;

/// Infers function results from reachable returns and resolved normal completion.
@UtilityClass
final class HirFunctionReturnTyping {

  static void resolve(final Hir.Function function) {
    final var signature = function.signature();
    final var constraint = signature.returnTypeAnnotation().ty();
    if (Tys.containsInferred(constraint)) {
      final var result = HirBodyResultTyping.resolve(function.body());
      signature.ty(signature.ty().toBuilder().returnTy(HirDeclarationTyping.resolve(constraint, result)).build());
    }
  }
}
