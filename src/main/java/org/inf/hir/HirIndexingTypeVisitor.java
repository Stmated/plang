package org.inf.hir;

import jakarta.annotation.Nullable;
import org.inf.ty.Ty;
import org.inf.ty.util.Tys;

/// Reads resolved receiver and accessor types for indexing, independently of completion.
public final class HirIndexingTypeVisitor implements HirVisitor {

  private Ty type;

  private HirIndexingTypeVisitor() {
  }

  @Nullable
  public static Ty find(final Hir.Expression expression) {
    final var visitor = new HirIndexingTypeVisitor();
    visitor.visitResult(expression);
    return visitor.type;
  }

  private void visitResult(final Hir.Expression expression) {
    type = expression.ty();
    expression.visit(this);
  }

  @Override
  public void visitChild(final Hir.Expression expression) {
  }

  @Override
  public void visitBlock(final Hir.Block expression) {
    visitResult(expression.children());
  }

  @Override
  public void visitExpressions(final Hir.Expressions expression) {
    final var children = expression.children();
    if (children.length != 0) {
      visitResult(children[children.length - 1]);
    }
  }

  @Override
  public void visitCallArgument(final Hir.Argument expression) {
    visitResult(expression.value());
  }

  @Override
  public void visitTupleEntry(final Hir.TupleEntry expression) {
    visitResult(expression.value());
  }

  @Override
  public void visitAssignment(final Hir.Assignment expression) {
    // Syntactic diagnostics may inspect the RHS without making the assignment value-producing.
    visitResult(expression.rhs());
  }

  @Override
  public void visitDec(final Hir.Dec expression) {
    type = expression.resolvedTy();
  }

  @Override
  public void visitParameter(final Hir.Parameter expression) {
    type = expression.resolvedTy();
  }

  @Override
  public void visitConvert(final Hir.Convert expression) {
    type = expression.targetTy();
  }

  @Override
  public void visitCall(final Hir.Call expression) {
    final var signature = Tys.getCallableSignature(expression.target());
    if (signature != null) {
      type = signature.returnTy();
    }
  }

  @Override
  public void visitDotAccess(final Hir.DotAccess expression) {
    type = Tys.getMemberTy(expression);
  }

  @Override
  public void visitArrayAccess(final Hir.ArrayAccess expression) {
    type = Tys.getIndexedTy(expression);
  }

  @Override
  public void visitArray(final Hir.Array expression) {
    type = expression.arrayTy();
  }

  @Override
  public void visitRange(final Hir.Range expression) {
    type = expression.rangeTy();
  }

  @Override
  public void visitConditional(final Hir.Conditional expression) {
    if (expression.pass() == null) {
      type = Ty.VOID;
    } else {
      visitResult(expression.pass());
    }
    final var pass = type;
    if (expression.fail() == null) {
      type = Ty.VOID;
    } else {
      visitResult(expression.fail());
    }
    type = Tys.union(pass, type);
  }
}
