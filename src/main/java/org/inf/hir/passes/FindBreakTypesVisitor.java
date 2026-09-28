package org.inf.hir.passes;

import java.util.ArrayList;
import java.util.List;
import org.inf.hir.Hir;
import org.inf.ty.Ty;

class FindBreakTypesVisitor extends HirExecutionVisitor {

  private final Hir.Expression root;
  private final List<Ty> types = new ArrayList<>();

  public static List<Ty> find(Hir.Expression expr) {
    final var visitor = new FindBreakTypesVisitor(expr);
    expr.visit(visitor);
    return visitor.types;
  }

  private FindBreakTypesVisitor(Hir.Expression root) {
    this.root = root;
  }

  @Override
  public void visitLoop(Hir.Loop maybeNested) {
    if (maybeNested == root) {
      // Only keep going if it's the first-level loop.
      super.visitLoop(maybeNested);
    }
  }

  @Override
  public void visitLoopBreak(Hir.LoopBreak expr) {
    if (expr.value() != null) {
      visitChild(expr.value());
    }
    if (continuing) {
      this.types.add(expr.value() == null ? Ty.VOID : expr.value().ty());
    }
    continuing = false;
  }
}
