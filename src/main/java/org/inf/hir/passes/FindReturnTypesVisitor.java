package org.inf.hir.passes;

import lombok.Getter;
import org.inf.hir.Hir;
import org.inf.ty.Ty;
import org.inf.ty.util.Tys;

final class FindReturnTypesVisitor extends HirExecutionVisitor {

  @Getter
  private Ty returnType;

  @Override
  public void visitReturn(Hir.Return expression) {
    visitChild(expression.expression());
    if (continuing) {
      returnType = Tys.union(returnType, expression.expression().ty());
    }
    continuing = false;
  }
}
