package org.inf.hir.passes;

import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.hir.util.Hirs;

public class HirValidationPass {

  public static void pass(final Hir.Expression root) {
    root.visit(new Visitor());
  }

  private static final class Visitor implements HirVisitor {

    @Override
    public void visitNewByBlock(Hir.NewByBlock expr) {
      HirVisitor.super.visitNewByBlock(expr);

      final var owner = Hirs.getConstructionTargetLayout(expr.target());
      if (owner != null) {
        for (final var initializer : expr.fields()) {
          if (Hirs.getInitializerField(expr, initializer) == null) {
            throw new IllegalArgumentException("Unknown struct field: %s".formatted(initializer.lhs()));
          }
        }
      }
    }
  }
}
