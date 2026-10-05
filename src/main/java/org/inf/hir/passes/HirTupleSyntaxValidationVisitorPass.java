package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;

/// Check label syntax before resolving names or interpreting type expressions.
@UtilityClass
public class HirTupleSyntaxValidationVisitorPass {

  public static void pass(final Hir.Expression expression) {
    expression.visit(new Visitor());
  }

  private static final class Visitor implements HirVisitor {

    private boolean inAnnotation;
    private boolean inTuple;

    private void visitAnnotation(final Hir.Expression expression) {
      final var outer = inAnnotation;
      try {
        inAnnotation = true;
        visitChild(expression);
      } finally {
        inAnnotation = outer;
      }
    }

    @Override
    public void visitDecType(final Hir.Expression expression) {
      visitAnnotation(expression);
    }

    @Override
    public void visitParameterType(final Hir.Expression expression) {
      visitAnnotation(expression);
    }

    @Override
    public void visitFunctionSignatureReturnType(final Hir.Expression expression) {
      visitAnnotation(expression);
    }

    @Override
    public void visitArrayElementType(final Hir.Expression expression) {
      visitAnnotation(expression);
    }

    @Override
    public void visitTuple(final Hir.Tuple expression) {
      final var outer = inTuple;
      try {
        inTuple = true;
        HirVisitor.super.visitTuple(expression);
      } finally {
        inTuple = outer;
      }
    }

    @Override
    public void visitTupleEntry(final Hir.TupleEntry expression) {
      if (!inTuple) {
        throw new IllegalArgumentException("Singleton named tuples require a trailing comma");
      }
      if (expression.label() != null && expression.typeLabel() != inAnnotation) {
        throw new IllegalArgumentException(inAnnotation
          ? "Tuple type labels require ':'"
          : "Tuple value labels require '='");
      }
      final var outer = this.inTuple;
      try {
        this.inTuple = false;
        HirVisitor.super.visitTupleEntry(expression);
      } finally {
        this.inTuple = outer;
      }
    }
  }
}
