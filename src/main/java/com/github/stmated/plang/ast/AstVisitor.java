package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.model.*;

public interface AstVisitor<T> {

  T aggregate(T a, T b);

  T noValue();

  default T visit(AstExpression expr) {

    if (expr == null) {
      return noValue();
    }

    return expr.visit(this);
  }

  default T visit(AstExpression[] children) {

    if (children == null || children.length == 0) {
      return noValue();
    }

    T result = visit(children[0]);
    for (var i = 1; i < children.length; i++) {
      result = aggregate(result, visit(children[i]));
    }

    return result;
  }

  default T visitAssignment(AstAssignment expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitBecome(AstBecome expr) {
    return visit(expr.call());
  }

  default T visitBinaryOperation(AstBinaryOperation expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitBlock(AstBlock expr) {
    return visit(expr.children());
  }

  default T visitBracket(AstBracket expr) {
    return visit(expr.children());
  }

  default T visitCall(AstCall expr) {

    final var targetRes = visit(expr.target());
    final var parenRes = visit(expr.paren());

    return aggregate(targetRes, parenRes);
  }

  default T visitCallable(AstCallable expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitComment(AstComment expr) {
    return this.noValue();
  }

  default T visitConditional(AstConditional expr) {
    return aggregate(visit(expr.predicate()), aggregate(visit(expr.pass()), visit(expr.fail())));
  }

  default T visitDotAccess(AstDotAccess expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitExport(AstExport expr) {
    return visit(expr.exported());
  }

  default T visitExpressionCollection(AstExpressionCollection expr) {
    return visit(expr.children());
  }

  default T visitIdentifier(AstIdentifier expr) {
    return this.noValue();
  }

  default T visitImpl(AstImpl expr) {

    final var traitRes = visit(expr.traitIdentifier());
    final var forRes = visit(expr.forExpression());
    final var declarationRes = aggregate(traitRes, forRes);
    final var withRes = visit(expr.with());
    final var blockRes = visit(expr.block());

    return aggregate(declarationRes, aggregate(withRes, blockRes));
  }

  default T visitImport(AstImport expr) {
    return visit(expr.path());
  }

  default T visitImportPath(AstImportPath expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitImportPathAlias(AstImportPathAlias expr) {
    return aggregate(visit(expr.target()), visit(expr.alias()));
  }

  default T visitImportPathGroup(AstImportPathGroup expr) {
    return visit(expr.items());
  }

  default T visitImportPathIdentifier(AstImportPathIdentifier expr) {
    return visit(expr.identifier());
  }

  default T visitImportPathWildcard(AstImportPathWildcard expr) {
    return this.noValue();
  }

  default T visitLabeling(AstLabeling expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitLiteral(AstLiteral expr) {
    return this.noValue();
  }

  default T visitLoopDoWhile(AstLoopDoWhile expr) {
    return aggregate(visit(expr.predicate()), visit(expr.body()));
  }

  default T visitLoopFor(AstLoopFor expr) {

//    final var a = aggregate(visit(expr.assignments()), visit(expr.predicate()));
//    final var b = aggregate(visit(expr.steppers()), visit(expr.block()));

    return aggregate(visit(expr.head()), visit(expr.block()));
  }

  default T visitLoopWhile(AstLoopWhile expr) {
    return aggregate(visit(expr.predicate()), visit(expr.body()));
  }

  default T visitMatch(AstMatch expr) {
    return aggregate(visit(expr.target()), visit(expr.children()));
  }

  default T visitCompTime(AstCompTime expr) {
    return visit(expr.target());
  }

  default T visitNew(AstNew expr) {
    return aggregate(visit(expr.target()), visit(expr.expressions()));
  }

  default T visitNoOp(AstNoOp expr) {
    return this.noValue();
  }

  default T visitNot(AstNot expr) {
    return visit(expr.expression());
  }

  default T visitParen(AstParen expr) {
    return visit(expr.expression());
  }

  default T visitProgram(AstProgram expr) {
    return visit(expr.children());
  }

  default T visitRange(AstRange expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitReturn(AstReturn expr) {
    return visit(expr.expression());
  }

  default T visitStruct(AstStruct expr) {
    return visit(expr.block());
  }

  default T visitThen(AstThen expr) {
    return visit(expr.expression());
  }

  default T visitTrait(AstTrait expr) {
    return visit(expr.block());
  }

  default T visitType(AstType expr) {
    return visit(expr.identifier());
  }

  default T visitVarargs(AstVarargs expr) {
    return this.noValue();
  }

  default T visitVariableDeclaration(AstVariableDeclaration expr) {
    return aggregate(visit(expr.identifier()), visit(expr.type()));
  }

  default T visitVariableSink(AstVariableSink expr) {
    return this.noValue();
  }

  default T visitWhere(AstWhere expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitWith(AstWith expr) {
    return aggregate(visit(expr.argument()), visit(expr.block()));
  }

  default T visitYield(AstYield expr) {
    return visit(expr.expression());
  }

  default T visitTypePlaceholder(AstTypePlaceholder expr) {
    return visit(expr.identifier());
  }

  default T visitStaticAccess(AstStaticAccess expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitInfer(AstInfer expr) {
    return visit(expr.expression());
  }

  default T visitIn(AstIn expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitLoop(AstLoop expr) {
    return visit(expr.body());
  }
}
