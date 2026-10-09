package org.inf.ast;

public interface AstVisitor<T> {

  default T aggregate(T a, T b) {
    return noValue();
  }

  default T noValue() {
    return null;
  }

  default T aggregate(final T a, final T b, final T c) {
    return aggregate(aggregate(a, b), c);
  }

  default T visit(final Ast.Expression expr) {

    if (expr == null) {
      return noValue();
    }

    return expr.visit(this);
  }

  default T visit(final Ast.Expression... children) {

    if (children == null || children.length == 0) {
      return noValue();
    }

    T result = visit(children[0]);
    for (var i = 1; i < children.length; i++) {
      result = aggregate(result, visit(children[i]));
    }

    return result;
  }

  default T visitString(final String str) {
    return noValue();
  }

  default <E extends Enum<E>> T visitEnum(final Enum<E> e) {
    return noValue();
  }

  default T visitAssignment(final Ast.Assignment expr) {
    return visit(expr.lhs(), expr.rhs());
  }

  default T visitBecome(final Ast.Become expr) {
    return visit(expr.expression());
  }

  default T visitBinaryOperation(final Ast.BinaryOperation expr) {
    return aggregate(
      visitBinaryOperationLhs(expr.lhs()),
      visitBinaryOperationKind(expr.kind),
      visitBinaryOperationRhs(expr.rhs())
    );
  }

  default T visitBinaryOperationKind(final Ast.BinaryOperationKind kind) {
    return visitEnum(kind);
  }

  default T visitBinaryOperationLhs(final Ast.Expression expr) {
    return visit(expr);
  }

  default T visitBinaryOperationRhs(final Ast.Expression expr) {
    return visit(expr);
  }

  default T visitBlock(final Ast.Block expr) {
    return visit(expr.expression());
  }

  default T visitBracket(final Ast.Bracket expr) {
    return visit(expr.children());
  }

  default T visitPostfixExpression(final Ast.PostfixExpression expr) {
    return visit(expr.target(), expr.suffix());
  }

  default T visitJuxtaposition(final Ast.Juxtaposition expr) {
    return aggregate(visit(expr.target()), visit(expr.arguments()));
  }

  default T visitCallable(final Ast.Callable expr) {
    return visit(expr.lhs(), expr.rhs());
  }

  default <E extends Ast.Expression> T visitPartial(final Ast.Partial<E> expr) {
    return visit(expr.expression());
  }

  default T visitBubble(final Ast.Bubble expr) {
    return visit(expr.expression());
  }

  default T visitComment(final Ast.Comment expr) {
    return this.visitString(expr.content());
  }

  default T visitConditional(final Ast.Conditional expr) {
    return visit(expr.predicate(), expr.pass(), expr.fail());
  }

  default T visitDotAccess(final Ast.DotAccess expr) {
    return visit(expr.lhs(), expr.rhs());
  }

  default T visitExport(final Ast.Export expr) {
    return visit(expr.exported());
  }

  default T visitExpressions(final Ast.Expressions expr) {
    return visit(expr.children());
  }

  default T visitLexeme(final Ast.Lexeme expr) {
    return visitString(expr.name());
  }

  default T visitImpl(final Ast.Impl expr) {
    return visit(expr.traitLexeme(), expr.forExpression(), expr.with(), expr.block());
  }

  default T visitImport(final Ast.Import expr) {
    return visit(expr.path());
  }

  default T visitImportPath(final Ast.ImportPath expr) {
    return visit(expr.lhs(), expr.rhs());
  }

  default T visitImportPathAlias(final Ast.ImportPathAlias expr) {
    return visit(expr.target(), expr.alias());
  }

  default T visitImportPathGroup(final Ast.ImportPathGroup expr) {
    return visit(expr.items());
  }

  default T visitImportPathIdentifier(final Ast.ImportPathIdentifier expr) {
    return visit(expr.lexeme());
  }

  default T visitImportPathWildcard(final Ast.ImportPathWildcard expr) {
    return this.noValue();
  }

  default T visitLabeling(final Ast.Labeling expr) {
    return visit(expr.lhs(), expr.rhs());
  }

  default T visitLiteral(final Ast.Literal expr) {
    return visitString(expr.content());
  }

  default T visitLoopDoWhile(final Ast.LoopDoWhile expr) {
    return visit(expr.predicate(), expr.body());
  }

  default T visitLoopFor(final Ast.LoopFor expr) {
    return visit(expr.head(), expr.block());
  }

  default T visitLoopWhile(final Ast.LoopWhile expr) {
    return visit(expr.predicate(), expr.body());
  }

  default T visitMatch(final Ast.Match expr) {
    return visit(expr.target(), expr.children());
  }

  default T visitCompTime(final Ast.CompTime expr) {
    return visit(expr.target());
  }

  default T visitNew(final Ast.New expr) {
    return visit(expr.allocator(), expr.target(), expr.arguments());
  }

  default T visitNoOp(final Ast.NoOp expr) {
    return this.noValue();
  }

  default T visitComma(final Ast.Comma expr) {
    return this.noValue();
  }

  default T visitNot(final Ast.Not expr) {
    return visit(expr.expression());
  }

  default T visitParen(final Ast.Paren expr) {
    return visit(expr.expression());
  }

  default T visitProgram(final Ast.Program expr) {
    return visit(expr.children());
  }

  default T visitRange(final Ast.Range expr) {
    return visit(expr.lhs(), expr.rhs());
  }

  default T visitReturn(final Ast.Return expr) {
    return visit(expr.expression());
  }

  default T visitStruct(final Ast.Struct expr) {
    return visit(expr.block());
  }

  default T visitThen(final Ast.Then expr) {
    return visit(expr.expression());
  }

  default T visitTrait(final Ast.Trait expr) {
    return visit(expr.block());
  }

  default T visitType(final Ast.Type expr) {
    return visit(expr.lexeme());
  }

  default T visitVariableDeclaration(final Ast.VariableDeclaration expr) {
    return visit(expr.lexeme(), expr.type());
  }

  default T visitVariableSink(final Ast.VariableSink expr) {
    return this.noValue();
  }

  default T visitWhere(final Ast.Where expr) {
    return visit(expr.lhs(), expr.rhs());
  }

  default T visitWith(final Ast.With expr) {
    return visit(expr.argument(), expr.block());
  }

  default T visitYield(final Ast.Yield expr) {
    return visit(expr.expression());
  }

  default T visitTypePlaceholder(final Ast.TypePlaceholder expr) {
    return visit(expr.lexeme());
  }

  default T visitStaticAccess(final Ast.StaticAccess expr) {
    return visit(expr.lhs(), expr.rhs());
  }

  default T visitInfer(final Ast.Infer expr) {
    return visit(expr.expression());
  }

  default T visitIn(final Ast.In expr) {
    return visit(expr.lhs(), expr.rhs());
  }

  default T visitLoop(final Ast.Loop expr) {
    return visit(expr.body());
  }

  default T visitNegate(final Ast.Negate expr) {
    return visit(expr.expression());
  }

  default T visitSpread(final Ast.Spread expr) {
    return visit(expr.expression());
  }

  default T visitRest(final Ast.Rest expr) {
    return noValue();
  }
}
