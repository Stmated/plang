package org.inf.hir;

import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.inf.hir.util.Hirs;
import org.inf.ty.Ty;
import org.inf.ty.util.Tys;

/// Reads receiver/accessor types for indexing diagnostics, not runtime evaluation permission.
/// Diverging tuples expose `DEADEND` instead of retained fields; calls expose current return types.
/// Array/range metadata still identifies elements and slices even when evaluation cannot complete.
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HirIndexingTypeVisitor implements HirVisitor {

  private Ty type;

  @Nullable
  public static Ty find(final Hir.Expression expression) {
    final var visitor = new HirIndexingTypeVisitor();
    visitor.visitResult(expression);
    return visitor.type;
  }

  private void visitResult(final Hir.Expression expression) {
    type = Tys.structuralTy(expression.ty());
    if (type == null) {
      type = Tys.completionTy(expression.ty());
    }
    expression.visit(this);
  }

  @Override
  public void visitTuple(final Hir.Tuple expression) {
    if (Tys.isDeadend(expression.ty())) {
      type = Ty.DEADEND;
    }
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
    type = Tys.completionTy(Hirs.getBindingTy(expression));
  }

  @Override
  public void visitParameter(final Hir.Parameter expression) {
    type = Tys.structuralTy(expression.ty());
  }

  @Override
  public void visitIdentifier(final Hir.Identifier expression) {
    if (expression.target() != null) {
      visitResult(expression.target());
    }
  }

  @Override
  public void visitConvert(final Hir.Convert expression) {
    type = expression.targetTy();
  }

  @Override
  public void visitCall(final Hir.Call expression) {
    final var signature = Hirs.getCallableSignature(expression.target());
    if (signature != null) {
      type = signature.returnTy();
    }
  }

  @Override
  public void visitDotAccess(final Hir.DotAccess expression) {
    type = Hirs.getMemberTy(expression);
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
