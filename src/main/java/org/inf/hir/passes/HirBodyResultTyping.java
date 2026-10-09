package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.ty.Ty;
import org.inf.ty.util.Tys;

/// Resolves external results from reachable returns and normal body completion.
@UtilityClass
final class HirBodyResultTyping {

  static Ty resolve(final Hir.Expression body) {
    final var returns = new FindReturnTypesVisitor();
    body.visit(returns);
    return Tys.union(returns.returnType(), body.ty());
  }
}
