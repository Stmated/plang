package org.inf.hir.passes;

import lombok.Getter;
import org.inf.hir.Hir;
import org.inf.ty.Ty;
import org.inf.ty.util.Tys;

import java.util.ArrayList;
import java.util.List;

public final class FindReturnTypesVisitor extends HirExecutionVisitor {

  @Getter
  private final List<Ty> returnTypes = new ArrayList<>();

  @Override
  public void visitReturn(Hir.Return expression) {
    visitChild(expression.expression());
    if (continuing) {
      returnTypes.add(Tys.completionTy(expression.expression().ty()));
    }
    continuing = false;
  }
}
