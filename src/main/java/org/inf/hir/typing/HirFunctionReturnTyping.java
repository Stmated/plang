package org.inf.hir.typing;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.ty.util.Tys;

/// Infers function results from reachable returns and resolved normal completion.
@UtilityClass
public final class HirFunctionReturnTyping {

  public static void resolve(final Hir.Function function) {
    final var signature = function.signature();
    final var constraint = signature.returnTypeAnnotation().ty();
    if (Tys.containsInferred(constraint)) {
      final var result = HirBodyResultTyping.resolve(function.body());
      signature.ty(signature.ty().toBuilder().returnTy(HirDeclarationTyping.resolve(constraint, result)).build());
    }
  }
}
