package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.util.Tys;

/// Rejects unresolved signatures after their available sources of inference have been resolved.
@UtilityClass
public class HirFunctionSignatureValidationVisitorPass {

  public static void pass(final Hir.Expression expression) {
    expression.visit(new Visitor(false));
  }

  public static void passStandalone(final Hir.Expression expression) {
    expression.visit(new Visitor(true));
  }

  private record Visitor(boolean standaloneOnly) implements HirVisitor {

    @Override
    public void visitFunction(final Hir.Function expression) {
      if (standaloneOnly) {
        HirVisitor.super.visitFunctionSignature(expression.signature());
        visitFunctionBody(expression.body());
      } else {
        HirVisitor.super.visitFunction(expression);
      }
    }

    @Override
    public void visitFunctionSignature(final Hir.FunctionSignature expression) {
      HirVisitor.super.visitFunctionSignature(expression);
      if (Tys.containsInferred(expression.ty())) {
        throw new IllegalArgumentException("Function signature requires resolved parameter and return types: " + expression);
      }
    }
  }
}
