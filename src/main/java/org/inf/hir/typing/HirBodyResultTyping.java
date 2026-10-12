package org.inf.hir.typing;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.passes.FindReturnTypesVisitor;
import org.inf.ty.Ty;
import org.inf.ty.util.Tys;

import java.util.ArrayList;

/// Resolves external results from reachable returns and normal body completion.
@UtilityClass
public final class HirBodyResultTyping {

  public static Ty resolve(final Hir.Expression body) {
    final var returns = new FindReturnTypesVisitor();
    body.visit(returns);
    if (returns.returnTypes().isEmpty()) {
      return Tys.completionTy(body.ty());
    }

    final var returnTypes = new ArrayList<>(returns.returnTypes());
    returnTypes.add(Tys.completionTy(body.ty()));

    return Tys.union(returnTypes.toArray(new Ty[0]));
  }
}
