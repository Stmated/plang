package org.inf.hir.passes;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.inf.hir.Hir;
import org.inf.ty.Ty;
import org.inf.ty.util.Tys;

@RequiredArgsConstructor
class FindBreakTypesVisitor extends HirExecutionVisitor {

  private final Hir.Expression root;
  private final List<Ty> types = new ArrayList<>();

  public static List<Ty> find(Hir.Expression expr) {
    final var visitor = new FindBreakTypesVisitor(expr);
    expr.visit(visitor);
    return visitor.types;
  }

  @Override
  public void visitLoop(Hir.Loop loop) {
    if (loop == root) {
      // Only keep going if it's the first-level loop.
      super.visitLoop(loop);
    }
  }
  public void visitLoopBreak(Hir.LoopBreak expr) {
    super.visitLoopBreak(expr);

    if (continuing) {
      this.types.add(expr.value() == null ? Ty.VOID : Tys.completionTy(expr.value().ty()));
    }
    continuing = false;
  }
}
