package com.github.stmated.plang.ast;

public interface AstVisitor<T> {

  T aggregate(T a, T b);

  T noValue();

  default T visit(Ast.Expression expr) {

    if (expr == null) {
      return noValue();
    }

    return expr.visit(this);
  }

  default T visit(Ast.Expression[] children) {

    if (children == null || children.length == 0) {
      return noValue();
    }

    T result = visit(children[0]);
    for (var i = 1; i < children.length; i++) {
      result = aggregate(result, visit(children[i]));
    }

    return result;
  }

  default T visitAssignment(Ast.Assignment expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitBecome(Ast.Become expr) {
    return visit(expr.call());
  }

  default T visitBinaryOperation(Ast.BinaryOperation expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitBlock(Ast.Block expr) {
    return visit(expr.expression());
  }

  default T visitBracket(Ast.Bracket expr) {
    return visit(expr.children());
  }

  default T visitCall(Ast.Call expr) {

    final var targetRes = visit(expr.target());
    final var parenRes = visit(expr.paren());

    return aggregate(targetRes, parenRes);
  }

  default T visitCallable(Ast.Callable expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitComment(Ast.Comment expr) {
    return this.noValue();
  }

  default T visitConditional(Ast.Conditional expr) {
    return aggregate(visit(expr.predicate()), aggregate(visit(expr.pass()), visit(expr.fail())));
  }

  default T visitDotAccess(Ast.DotAccess expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitExport(Ast.Export expr) {
    return visit(expr.exported());
  }

  default T visitExpressionCollection(Ast.Expressions expr) {
    return visit(expr.children());
  }

  default T visitIdentifier(Ast.Identifier expr) {
    return this.noValue();
  }

  default T visitImpl(Ast.Impl expr) {

    final var traitRes = visit(expr.traitIdentifier());
    final var forRes = visit(expr.forExpression());
    final var declarationRes = aggregate(traitRes, forRes);
    final var withRes = visit(expr.with());
    final var blockRes = visit(expr.block());

    return aggregate(declarationRes, aggregate(withRes, blockRes));
  }

  default T visitImport(Ast.Import expr) {
    return visit(expr.path());
  }

  default T visitImportPath(Ast.ImportPath expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitImportPathAlias(Ast.ImportPathAlias expr) {
    return aggregate(visit(expr.target()), visit(expr.alias()));
  }

  default T visitImportPathGroup(Ast.ImportPathGroup expr) {
    return visit(expr.items());
  }

  default T visitImportPathIdentifier(Ast.ImportPathIdentifier expr) {
    return visit(expr.identifier());
  }

  default T visitImportPathWildcard(Ast.ImportPathWildcard expr) {
    return this.noValue();
  }

  default T visitLabeling(Ast.Labeling expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitLiteral(Ast.Literal expr) {
    return this.noValue();
  }

  default T visitLoopDoWhile(Ast.LoopDoWhile expr) {
    return aggregate(visit(expr.predicate()), visit(expr.body()));
  }

  default T visitLoopFor(Ast.LoopFor expr) {
    return aggregate(visit(expr.head()), visit(expr.block()));
  }

  default T visitLoopWhile(Ast.LoopWhile expr) {
    return aggregate(visit(expr.predicate()), visit(expr.body()));
  }

  default T visitMatch(Ast.Match expr) {
    return aggregate(visit(expr.target()), visit(expr.children()));
  }

  default T visitCompTime(Ast.CompTime expr) {
    return visit(expr.target());
  }

  default T visitNew(Ast.New expr) {
    return aggregate(aggregate(visit(expr.allocator()), visit(expr.target())), visit(expr.arguments()));
  }

  default T visitNoOp(Ast.NoOp expr) {
    return this.noValue();
  }

  default T visitNot(Ast.Not expr) {
    return visit(expr.expression());
  }

  default T visitParen(Ast.Paren expr) {
    return visit(expr.expression());
  }

  default T visitProgram(Ast.Program expr) {
    return visit(expr.children());
  }

  default T visitRange(Ast.Range expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitReturn(Ast.Return expr) {
    return visit(expr.expression());
  }

  default T visitStruct(Ast.Struct expr) {
    return visit(expr.block());
  }

  default T visitThen(Ast.Then expr) {
    return visit(expr.expression());
  }

  default T visitTrait(Ast.Trait expr) {
    return visit(expr.block());
  }

  default T visitType(Ast.Type expr) {
    return visit(expr.identifier());
  }

  default T visitVariableDeclaration(Ast.VariableDeclaration expr) {
    return aggregate(visit(expr.identifier()), visit(expr.type()));
  }

  default T visitVariableSink(Ast.VariableSink expr) {
    return this.noValue();
  }

  default T visitWhere(Ast.Where expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitWith(Ast.With expr) {
    return aggregate(visit(expr.argument()), visit(expr.block()));
  }

  default T visitYield(Ast.Yield expr) {
    return visit(expr.expression());
  }

  default T visitTypePlaceholder(Ast.TypePlaceholder expr) {
    return visit(expr.identifier());
  }

  default T visitStaticAccess(Ast.StaticAccess expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitInfer(Ast.Infer expr) {
    return visit(expr.expression());
  }

  default T visitIn(Ast.In expr) {
    return aggregate(visit(expr.lhs()), visit(expr.rhs()));
  }

  default T visitLoop(Ast.Loop expr) {
    return visit(expr.body());
  }

  default T visitNegate(Ast.Negate expr) {
    return visit(expr.expression());
  }

  default T visitSpread(Ast.Spread expr) {
    return visit(expr.expression());
  }

  default T visitBracketAccess(Ast.BracketAccess expr) {
    return aggregate(visit(expr.target()), visit(expr.accessor()));
  }

  default T visitRest(Ast.Rest expr) {
    return noValue();
  }
}
