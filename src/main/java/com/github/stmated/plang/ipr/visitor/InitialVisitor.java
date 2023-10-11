package com.github.stmated.plang.ipr.visitor;

import com.github.stmated.plang.ipr.InitialAssignment;
import com.github.stmated.plang.ipr.InitialBecome;
import com.github.stmated.plang.ipr.InitialBinaryOperation;
import com.github.stmated.plang.ipr.InitialBlock;
import com.github.stmated.plang.ipr.InitialBracket;
import com.github.stmated.plang.ipr.InitialCall;
import com.github.stmated.plang.ipr.InitialCallable;
import com.github.stmated.plang.ipr.InitialComment;
import com.github.stmated.plang.ipr.InitialConditional;
import com.github.stmated.plang.ipr.InitialDotAccess;
import com.github.stmated.plang.ipr.InitialExport;
import com.github.stmated.plang.ipr.InitialExpression;
import com.github.stmated.plang.ipr.InitialExpressionCollection;
import com.github.stmated.plang.ipr.InitialIdentifier;
import com.github.stmated.plang.ipr.InitialImpl;
import com.github.stmated.plang.ipr.InitialImport;
import com.github.stmated.plang.ipr.InitialImportPath;
import com.github.stmated.plang.ipr.InitialImportPathAlias;
import com.github.stmated.plang.ipr.InitialImportPathGroup;
import com.github.stmated.plang.ipr.InitialImportPathIdentifier;
import com.github.stmated.plang.ipr.InitialImportPathWildcard;
import com.github.stmated.plang.ipr.InitialLabeling;
import com.github.stmated.plang.ipr.InitialLiteral;
import com.github.stmated.plang.ipr.InitialLoopDoWhile;
import com.github.stmated.plang.ipr.InitialLoopFor;
import com.github.stmated.plang.ipr.InitialLoopForEach;
import com.github.stmated.plang.ipr.InitialLoopWhile;
import com.github.stmated.plang.ipr.InitialMatch;
import com.github.stmated.plang.ipr.InitialCompTime;
import com.github.stmated.plang.ipr.InitialNew;
import com.github.stmated.plang.ipr.InitialNoOp;
import com.github.stmated.plang.ipr.InitialNot;
import com.github.stmated.plang.ipr.InitialParen;
import com.github.stmated.plang.ipr.InitialProgram;
import com.github.stmated.plang.ipr.InitialRange;
import com.github.stmated.plang.ipr.InitialReturn;
import com.github.stmated.plang.ipr.InitialStaticAccess;
import com.github.stmated.plang.ipr.InitialStruct;
import com.github.stmated.plang.ipr.InitialThen;
import com.github.stmated.plang.ipr.InitialTrait;
import com.github.stmated.plang.ipr.InitialType;
import com.github.stmated.plang.ipr.InitialTypePlaceholder;
import com.github.stmated.plang.ipr.InitialVarargs;
import com.github.stmated.plang.ipr.InitialVariableDeclaration;
import com.github.stmated.plang.ipr.InitialVariableSink;
import com.github.stmated.plang.ipr.InitialWhere;
import com.github.stmated.plang.ipr.InitialWith;
import com.github.stmated.plang.ipr.InitialYield;

public interface InitialVisitor<T> {

  T aggregate(T a, T b);

  T noValue();

  default T visit(InitialExpression expr) {

    if (expr == null) {
      return noValue();
    }

    return expr.visit(this);
  }

  default T visit(InitialExpression[] children) {

    if (children == null || children.length == 0) {
      return noValue();
    }

    T result = visit(children[0]);
    for (var i = 1; i < children.length; i++) {
      result = aggregate(result, visit(children[i]));
    }

    return result;
  }

  default T visitAssignment(InitialAssignment expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitBecome(InitialBecome expr) {
    return visit(expr.call());
  }

  default T visitBinaryOperation(InitialBinaryOperation expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitBlock(InitialBlock expr) {
    return visit(expr.children());
  }

  default T visitBracket(InitialBracket expr) {
    return visit(expr.children());
  }

  default T visitCall(InitialCall expr) {

    final var targetRes = visit(expr.target());
    final var parenRes = visit(expr.paren());

    return aggregate(targetRes, parenRes);
  }

  default T visitCallable(InitialCallable expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitComment(InitialComment expr) {
    return this.noValue();
  }

  default T visitConditional(InitialConditional expr) {
    return aggregate(visit(expr.predicate()), aggregate(visit(expr.pass()), visit(expr.fail())));
  }

  default T visitDotAccess(InitialDotAccess expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitExport(InitialExport expr) {
    return visit(expr.exported());
  }

  default <E extends InitialExpression> T visitExpressionCollection(InitialExpressionCollection<E> expr) {
    return visit(expr.children());
  }

  default T visitIdentifier(InitialIdentifier expr) {
    return this.noValue();
  }

  default T visitImpl(InitialImpl expr) {

    final var traitRes = visit(expr.traitIdentifier());
    final var forRes = visit(expr.forExpression());
    final var declarationRes = aggregate(traitRes, forRes);
    final var withRes = visit(expr.with());
    final var blockRes = visit(expr.block());

    return aggregate(declarationRes, aggregate(withRes, blockRes));
  }

  default T visitImport(InitialImport expr) {
    return visit(expr.path());
  }

  default T visitImportPath(InitialImportPath expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitImportPathAlias(InitialImportPathAlias expr) {
    return aggregate(visit(expr.target()), visit(expr.alias()));
  }

  default T visitImportPathGroup(InitialImportPathGroup expr) {
    return visit(expr.items());
  }

  default T visitImportPathIdentifier(InitialImportPathIdentifier expr) {
    return visit(expr.identifier());
  }

  default T visitImportPathWildcard(InitialImportPathWildcard expr) {
    return this.noValue();
  }

  default T visitLabeling(InitialLabeling expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitLiteral(InitialLiteral expr) {
    return this.noValue();
  }

  default T visitLoopDoWhile(InitialLoopDoWhile expr) {
    return aggregate(visit(expr.predicate()), visit(expr.body()));
  }

  default T visitLoopFor(InitialLoopFor expr) {

    final var a = aggregate(visit(expr.assignments()), visit(expr.predicate()));
    final var b = aggregate(visit(expr.steppers()), visit(expr.block()));

    return aggregate(a, b);
  }

  default T visitLoopForEach(InitialLoopForEach expr) {
    return aggregate(aggregate(visit(expr.target()), visit(expr.source())), visit(expr.body()));
  }

  default T visitLoopWhile(InitialLoopWhile expr) {
    return aggregate(visit(expr.predicate()), visit(expr.body()));
  }

  default T visitMatch(InitialMatch expr) {
    return aggregate(visit(expr.target()), visit(expr.children()));
  }

  default T visitCompTime(InitialCompTime expr) {
    return visit(expr.target());
  }

  default T visitNew(InitialNew expr) {
    return aggregate(visit(expr.target()), visit(expr.expressions()));
  }

  default T visitNoOp(InitialNoOp expr) {
    return this.noValue();
  }

  default T visitNot(InitialNot expr) {
    return visit(expr.expression());
  }

  default T visitParen(InitialParen expr) {
    return visit(expr.expression());
  }

  default T visitProgram(InitialProgram expr) {
    return visit(expr.children());
  }

  default T visitRange(InitialRange expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitReturn(InitialReturn expr) {
    return visit(expr.expression());
  }

  default T visitStruct(InitialStruct expr) {
    return visit(expr.block());
  }

  default T visitThen(InitialThen expr) {
    return visit(expr.expression());
  }

  default T visitTrait(InitialTrait expr) {
    return visit(expr.block());
  }

  default T visitType(InitialType expr) {
    return visit(expr.identifier());
  }

  default T visitVarargs(InitialVarargs expr) {
    return this.noValue();
  }

  default T visitVariableDeclaration(InitialVariableDeclaration expr) {
    return aggregate(visit(expr.identifier()), visit(expr.type()));
  }

  default T visitVariableSink(InitialVariableSink expr) {
    return this.noValue();
  }

  default T visitWhere(InitialWhere expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitWith(InitialWith expr) {
    return aggregate(visit(expr.argument()), visit(expr.block()));
  }

  default T visitYield(InitialYield expr) {
    return visit(expr.expression());
  }

  default T visitTypePlaceholder(InitialTypePlaceholder expr) {
    return visit(expr.identifier());
  }

  default T visitStaticAccess(InitialStaticAccess expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }
}
