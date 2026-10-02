package org.inf.ast.passes.implicit_calls;

import org.inf.ast.Ast;
import org.inf.ast.AstTransformer;

/**
 * Only these low-precedence owners share the remaining siblings with their rightmost child.
 * All other child visits use the enclosing pass and therefore start an independent scope.
 */
final class TailTransformer implements AstTransformer {

  private final AstExpressionGroupingTransformer groupingTransformer;
  private final Cursor cursor;

  TailTransformer(AstExpressionGroupingTransformer groupingTransformer, final Cursor cursor) {
    this.groupingTransformer = groupingTransformer;
    this.cursor = cursor;
  }

  @Override
  public Ast.Expression visit(final Ast.Expression expression) {
    return groupingTransformer.visit(expression);
  }

  @Override
  public Ast.Expression visitAssignment(final Ast.Assignment expr) {
    return new Ast.Assignment(visit(expr.lhs()), cursor.extend(expr.rhs()));
  }

  @Override
  public Ast.Expression visitCallable(final Ast.Callable expr) {
    return new Ast.Callable(visit(expr.lhs()), cursor.extend(expr.rhs()));
  }

  @Override
  public Ast.Expression visitReturn(final Ast.Return expr) {
    return new Ast.Return(cursor.extend(expr.expression()));
  }

  @Override
  public Ast.Expression visitThen(final Ast.Then expr) {
    return new Ast.Then(cursor.extend(expr.expression()));
  }

  @Override
  public Ast.Expression visitYield(final Ast.Yield expr) {
    return new Ast.Yield(cursor.extend(expr.expression()));
  }

  @Override
  public Ast.Expression visitExpressions(final Ast.Expressions expr) {
    return groupingTransformer.visitExpressions(expr);
  }

  @Override
  public Ast.Expression visitParen(final Ast.Paren expr) {
    return groupingTransformer.visitParen(expr);
  }

  @Override
  public Ast.Expression visitBlock(final Ast.Block expr) {
    return groupingTransformer.visitBlock(expr);
  }

  @Override
  public Ast.Expression visitBracket(final Ast.Bracket expr) {
    return groupingTransformer.visitBracket(expr);
  }

  @Override
  public Ast.Expression visitNew(final Ast.New expr) {
    return groupingTransformer.visitNew(expr);
  }

  @Override
  public Ast.Expression visitMatch(final Ast.Match expr) {
    return groupingTransformer.visitMatch(expr);
  }

  @Override
  public Ast.Expression visitComma(final Ast.Comma expr) {
    return groupingTransformer.visitComma(expr);
  }
}
