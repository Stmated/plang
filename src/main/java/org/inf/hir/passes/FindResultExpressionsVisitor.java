package org.inf.hir.passes;

import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;

import java.util.ArrayList;
import java.util.List;

/// Finds value-producing expressions without resolving types or entering unrelated subexpressions.
final class FindResultExpressionsVisitor implements HirVisitor {

  private final List<Hir.Expression> results = new ArrayList<>();

  static List<Hir.Expression> find(final Hir.Expression expression) {
    final var visitor = new FindResultExpressionsVisitor();
    visitor.visitChild(expression);
    return visitor.results;
  }

  static List<Hir.Expression> findReturns(final Hir.Expression body) {
    final var results = find(body);
    body.visit(new HirVisitor() {
      @Override
      public void visitFunction(final Hir.Function expression) {
      }

      @Override
      public void visitFunctionSignature(final Hir.FunctionSignature expression) {
      }

      @Override
      public void visitReturn(final Hir.Return expression) {
        HirVisitor.super.visitReturn(expression);
        results.addAll(find(expression.expression()));
      }
    });
    return results;
  }

  @Override
  public void visitChild(final Hir.Expression expression) {
    if (expression instanceof Hir.Return) {
      return;
    }
    if (expression instanceof Hir.Block || expression instanceof Hir.Expressions || expression instanceof Hir.Conditional) {
      expression.visit(this);
    } else {
      results.add(expression);
    }
  }

  @Override
  public void visitExpressions(final Hir.Expressions expression) {
    final var children = expression.children();
    if (children.length != 0) {
      visitChild(children[children.length - 1]);
    }
  }

  @Override
  public void visitConditional(final Hir.Conditional expression) {
    if (expression.pass() != null) {
      visitChild(expression.pass());
    }
    if (expression.fail() != null) {
      visitChild(expression.fail());
    }
  }
}
