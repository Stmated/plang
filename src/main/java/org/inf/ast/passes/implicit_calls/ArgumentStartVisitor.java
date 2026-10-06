package org.inf.ast.passes.implicit_calls;

import org.inf.ast.Ast;
import org.inf.ast.AstVisitor;

final class ArgumentStartVisitor implements AstVisitor<ArgumentStart> {

  @Override
  public ArgumentStart aggregate(final ArgumentStart a, final ArgumentStart b) {
    return ArgumentStart.NONE;
  }

  @Override
  public ArgumentStart noValue() {
    return ArgumentStart.NONE;
  }

  @Override
  public ArgumentStart visitLexeme(final Ast.Lexeme expr) {
    return ArgumentStart.FIRST;
  }

  @Override
  public ArgumentStart visitLiteral(final Ast.Literal expr) {
    return ArgumentStart.FIRST;
  }

  @Override
  public ArgumentStart visitNot(final Ast.Not expr) {
    return ArgumentStart.FIRST;
  }

  @Override
  public ArgumentStart visitParen(final Ast.Paren expr) {
    return ArgumentStart.AFTER_COMMA;
  }

  @Override
  public ArgumentStart visitBracket(final Ast.Bracket expr) {
    return ArgumentStart.AFTER_COMMA;
  }

  @Override
  public ArgumentStart visitNegate(final Ast.Negate expr) {
    return ArgumentStart.AFTER_COMMA;
  }

  @Override
  public ArgumentStart visitAssignment(final Ast.Assignment expr) {
    return visit(expr.lhs());
  }

  @Override
  public ArgumentStart visitCallable(final Ast.Callable expr) {
    return visit(expr.lhs());
  }

  @Override
  public ArgumentStart visitBinaryOperation(final Ast.BinaryOperation expr) {
    return visit(expr.lhs());
  }

  @Override
  public ArgumentStart visitLabeling(final Ast.Labeling expr) {
    return visit(expr.lhs());
  }

  @Override
  public ArgumentStart visitDotAccess(final Ast.DotAccess expr) {
    return visit(expr.lhs());
  }

  @Override
  public ArgumentStart visitStaticAccess(final Ast.StaticAccess expr) {
    return visit(expr.lhs());
  }

  @Override
  public ArgumentStart visitPostfixExpression(final Ast.PostfixExpression expr) {
    return visit(expr.target());
  }

  @Override
  public ArgumentStart visitJuxtaposition(final Ast.Juxtaposition expr) {
    return visit(expr.target());
  }

  @Override
  public ArgumentStart visitRange(final Ast.Range expr) {
    return visit(expr.lhs());
  }

  @Override
  public ArgumentStart visitWhere(final Ast.Where expr) {
    return visit(expr.lhs());
  }

  @Override
  public ArgumentStart visitIn(final Ast.In expr) {
    return visit(expr.lhs());
  }

  @Override
  public ArgumentStart visitBubble(final Ast.Bubble expr) {
    return visit(expr.expression());
  }

  @Override
  public ArgumentStart visitBecome(final Ast.Become expr) {
    return ArgumentStart.NONE;
  }

  @Override
  public ArgumentStart visitBlock(final Ast.Block expr) {
    return ArgumentStart.NONE;
  }

  @Override
  public <E extends Ast.Expression> ArgumentStart visitPartial(final Ast.Partial<E> expr) {
    return ArgumentStart.NONE;
  }

  @Override
  public ArgumentStart visitExport(final Ast.Export expr) {
    return ArgumentStart.NONE;
  }

  @Override
  public ArgumentStart visitImport(final Ast.Import expr) {
    return ArgumentStart.NONE;
  }

  @Override
  public ArgumentStart visitCompTime(final Ast.CompTime expr) {
    return ArgumentStart.NONE;
  }

  @Override
  public ArgumentStart visitReturn(final Ast.Return expr) {
    return ArgumentStart.NONE;
  }

  @Override
  public ArgumentStart visitThen(final Ast.Then expr) {
    return ArgumentStart.NONE;
  }

  @Override
  public ArgumentStart visitYield(final Ast.Yield expr) {
    return ArgumentStart.NONE;
  }

  @Override
  public ArgumentStart visitType(final Ast.Type expr) {
    return ArgumentStart.NONE;
  }

  @Override
  public ArgumentStart visitTypePlaceholder(final Ast.TypePlaceholder expr) {
    return ArgumentStart.NONE;
  }

  @Override
  public ArgumentStart visitInfer(final Ast.Infer expr) {
    return ArgumentStart.NONE;
  }

  @Override
  public ArgumentStart visitLoop(final Ast.Loop expr) {
    return ArgumentStart.NONE;
  }

  @Override
  public ArgumentStart visitSpread(final Ast.Spread expr) {
    return ArgumentStart.FIRST;
  }
}
