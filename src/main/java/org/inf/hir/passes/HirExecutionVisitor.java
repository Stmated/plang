package org.inf.hir.passes;

import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;

/** Traverses potentially executed expressions using their already-resolved flow types. */
abstract class HirExecutionVisitor implements HirVisitor {

  protected boolean continuing = true;

  @Override
  public void visitChild(Hir.Expression expression) {
    if (continuing) {
      expression.visit(this);
      continuing &= expression.ty() != Ty.DEADEND;
    }
  }

  @Override
  public void visitConditional(Hir.Conditional expression) {
    visitChild(expression.predicate());
    if (!continuing) {
      return;
    }
    if (expression.pass() != null) {
      visitChild(expression.pass());
    }
    continuing = true;
    if (expression.fail() != null) {
      visitChild(expression.fail());
    }
    continuing = expression.ty() != Ty.DEADEND;
  }

  @Override
  public void visitBinaryOperation(Hir.BinaryOperation expression) {
    visitChild(expression.lhs());
    if (!continuing) {
      return;
    }
    visitChild(expression.rhs());
    // AND/OR can bypass a noncontinuing right operand.
    continuing = expression.ty() != Ty.DEADEND;
  }

  @Override
  public void visitLoop(Hir.Loop expression) {
    visitChild(expression.body());
    // A break ends the body, not the enclosing function's continuation.
    continuing = expression.ty() != Ty.DEADEND;
  }

  @Override
  public void visitArray(Hir.Array expression) {
    for (final var element : expression.elements()) {
      visitChild(element);
    }
    if (expression.length() != null) {
      visitChild(expression.length());
    }
  }

  @Override
  public void visitFunction(Hir.Function expression) {
    // Creating a function value does not execute its body.
  }

  @Override
  public void visitFunctionSignature(Hir.FunctionSignature expression) {
  }

  @Override
  public void visitDec(Hir.Dec expression) {
  }

  @Override
  public void visitParameter(Hir.Parameter expression) {
  }

  @Override
  public void visitStruct(Hir.Struct expression) {
  }

  @Override
  public void visitTrait(Hir.Trait expression) {
  }

  @Override
  public void visitAllocator(Hir.Identifier expression) {
  }
}
