package org.inf.ast;

public interface AstTransformer extends AstVisitor<Ast.Expression> {

  @Override
  default Ast.Expression aggregate(final Ast.Expression a, final Ast.Expression b) {
    throw new UnsupportedOperationException("Transform children with their owning node");
  }

  default <E extends Ast.Expression> E visitAs(final E expression, final Class<E> type) {
    return type.cast(visit(expression));
  }

  default Ast.Expression[] transformChildren(final Ast.Expression[] children) {
    Ast.Expression[] result = children;
    for (var i = 0; i < children.length; i++) {
      final var child = visit(children[i]);
      if (child != children[i]) {
        if (result == children) {
          result = new Ast.Expression[children.length];
          System.arraycopy(children, 0, result, 0, i);
        }
      }
      if (result != children) {
        result[i] = child;
      }
    }
    return result;
  }

  @Override
  default Ast.Expression visitAssignment(final Ast.Assignment expr) {
    return new Ast.Assignment(visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  default Ast.Expression visitBecome(final Ast.Become expr) {
    return new Ast.Become(visit(expr.expression()));
  }

  @Override
  default Ast.Expression visitBinaryOperation(final Ast.BinaryOperation expr) {
    return new Ast.BinaryOperation(
      visitBinaryOperationLhs(expr.lhs()), expr.kind(), visitBinaryOperationRhs(expr.rhs())
    );
  }

  @Override
  default Ast.Expression visitBlock(final Ast.Block expr) {
    return new Ast.Block(visit(expr.expression()));
  }

  @Override
  default Ast.Expression visitBracket(final Ast.Bracket expr) {
    return new Ast.Bracket(transformChildren(expr.children()));
  }

  @Override
  default Ast.Expression visitPostfixExpression(final Ast.PostfixExpression expr) {
    return new Ast.PostfixExpression(visit(expr.target()), visit(expr.suffix()));
  }

  @Override
  default Ast.Expression visitJuxtaposition(final Ast.Juxtaposition expr) {
    return new Ast.Juxtaposition(visit(expr.target()), transformChildren(expr.arguments()));
  }

  @Override
  default Ast.Expression visitCallable(final Ast.Callable expr) {
    return new Ast.Callable(visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  default <E extends Ast.Expression> Ast.Expression visitPartial(final Ast.Partial<E> expr) {
    return new Ast.Partial<>(visit(expr.expression()));
  }

  @Override
  default Ast.Expression visitBubble(final Ast.Bubble expr) {
    return new Ast.Bubble(visit(expr.expression()));
  }

  @Override
  default Ast.Expression visitComment(final Ast.Comment expr) {
    return expr;
  }

  @Override
  default Ast.Expression visitConditional(final Ast.Conditional expr) {
    return new Ast.Conditional(visit(expr.predicate()), visit(expr.pass()), visit(expr.fail()));
  }

  @Override
  default Ast.Expression visitDotAccess(final Ast.DotAccess expr) {
    return new Ast.DotAccess(visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  default Ast.Expression visitExport(final Ast.Export expr) {
    return new Ast.Export(visit(expr.exported()), expr.isDefault());
  }

  @Override
  default Ast.Expression visitExpressions(final Ast.Expressions expr) {
    return new Ast.Expressions(transformChildren(expr.children()));
  }

  @Override
  default Ast.Expression visitLexeme(final Ast.Lexeme expr) {
    return expr;
  }

  @Override
  default Ast.Expression visitImpl(final Ast.Impl expr) {
    return new Ast.Impl(
      visitAs(expr.traitLexeme(), Ast.Lexeme.class), visit(expr.forExpression()),
      visitAs(expr.block(), Ast.Block.class), visit(expr.with())
    );
  }

  @Override
  default Ast.Expression visitImport(final Ast.Import expr) {
    return new Ast.Import(visitAs(expr.path(), Ast.ImportCapable.class));
  }

  @Override
  default Ast.Expression visitImportPath(final Ast.ImportPath expr) {
    return new Ast.ImportPath(
      visitAs(expr.lhs(), Ast.ImportCapable.class), visitAs(expr.rhs(), Ast.ImportCapable.class)
    );
  }

  @Override
  default Ast.Expression visitImportPathAlias(final Ast.ImportPathAlias expr) {
    return new Ast.ImportPathAlias(
      visitAs(expr.alias(), Ast.Lexeme.class), visitAs(expr.target(), Ast.ImportCapable.class)
    );
  }

  @Override
  default Ast.Expression visitImportPathGroup(final Ast.ImportPathGroup expr) {
    final var items = expr.items();
    Ast.ImportCapable[] result = items;
    for (var i = 0; i < items.length; i++) {
      final var item = visitAs(items[i], Ast.ImportCapable.class);
      if (item != items[i] && result == items) {
        result = new Ast.ImportCapable[items.length];
        System.arraycopy(items, 0, result, 0, i);
      }
      if (result != items) {
        result[i] = item;
      }
    }
    return new Ast.ImportPathGroup(result);
  }

  @Override
  default Ast.Expression visitImportPathIdentifier(final Ast.ImportPathIdentifier expr) {
    return new Ast.ImportPathIdentifier(visitAs(expr.lexeme(), Ast.Lexeme.class));
  }

  @Override
  default Ast.Expression visitImportPathWildcard(final Ast.ImportPathWildcard expr) {
    return expr;
  }

  @Override
  default Ast.Expression visitLabeling(final Ast.Labeling expr) {
    return new Ast.Labeling(visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  default Ast.Expression visitLiteral(final Ast.Literal expr) {
    return expr;
  }

  @Override
  default Ast.Expression visitLoopDoWhile(final Ast.LoopDoWhile expr) {
    return new Ast.LoopDoWhile(visit(expr.body()), visit(expr.predicate()));
  }

  @Override
  default Ast.Expression visitLoopFor(final Ast.LoopFor expr) {
    return new Ast.LoopFor(visit(expr.head()), visit(expr.block()));
  }

  @Override
  default Ast.Expression visitLoopWhile(final Ast.LoopWhile expr) {
    return new Ast.LoopWhile(visit(expr.predicate()), visit(expr.body()));
  }

  @Override
  default Ast.Expression visitMatch(final Ast.Match expr) {
    return new Ast.Match(visit(expr.target()), visitAs(expr.children(), Ast.Expressions.class));
  }

  @Override
  default Ast.Expression visitCompTime(final Ast.CompTime expr) {
    return new Ast.CompTime(visit(expr.target()));
  }

  @Override
  default Ast.Expression visitNew(final Ast.New expr) {
    return new Ast.New(
      visit(expr.target()), visitAs(expr.allocator(), Ast.Lexeme.class), visit(expr.arguments())
    );
  }

  @Override
  default Ast.Expression visitNoOp(final Ast.NoOp expr) {
    return expr;
  }

  @Override
  default Ast.Expression visitComma(final Ast.Comma expr) {
    return expr;
  }

  @Override
  default Ast.Expression visitNot(final Ast.Not expr) {
    return new Ast.Not(visit(expr.expression()));
  }

  @Override
  default Ast.Expression visitParen(final Ast.Paren expr) {
    return new Ast.Paren(visit(expr.expression()));
  }

  @Override
  default Ast.Expression visitProgram(final Ast.Program expr) {
    return new Ast.Program(visit(expr.children()));
  }

  @Override
  default Ast.Expression visitRange(final Ast.Range expr) {
    return new Ast.Range(visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  default Ast.Expression visitReturn(final Ast.Return expr) {
    return new Ast.Return(visit(expr.expression()));
  }

  @Override
  default Ast.Expression visitStruct(final Ast.Struct expr) {
    return new Ast.Struct(visitAs(expr.block(), Ast.Block.class));
  }

  @Override
  default Ast.Expression visitThen(final Ast.Then expr) {
    return new Ast.Then(visit(expr.expression()));
  }

  @Override
  default Ast.Expression visitTrait(final Ast.Trait expr) {
    return new Ast.Trait(visitAs(expr.block(), Ast.Block.class));
  }

  @Override
  default Ast.Expression visitType(final Ast.Type expr) {
    return new Ast.Type(visitAs(expr.lexeme(), Ast.Lexeme.class));
  }

  @Override
  default Ast.Expression visitVariableDeclaration(final Ast.VariableDeclaration expr) {
    return new Ast.VariableDeclaration(
      visitAs(expr.lexeme(), Ast.Lexeme.class), expr.mutabilityKind(), visit(expr.type()), expr.ref()
    );
  }

  @Override
  default Ast.Expression visitVariableSink(final Ast.VariableSink expr) {
    return expr;
  }

  @Override
  default Ast.Expression visitWhere(final Ast.Where expr) {
    return new Ast.Where(visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  default Ast.Expression visitWith(final Ast.With expr) {
    return new Ast.With(visit(expr.argument()), visitAs(expr.block(), Ast.Block.class));
  }

  @Override
  default Ast.Expression visitYield(final Ast.Yield expr) {
    return new Ast.Yield(visit(expr.expression()));
  }

  @Override
  default Ast.Expression visitTypePlaceholder(final Ast.TypePlaceholder expr) {
    return new Ast.TypePlaceholder(visitAs(expr.lexeme(), Ast.Lexeme.class));
  }

  @Override
  default Ast.Expression visitStaticAccess(final Ast.StaticAccess expr) {
    return new Ast.StaticAccess(visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  default Ast.Expression visitInfer(final Ast.Infer expr) {
    return new Ast.Infer(visit(expr.expression()));
  }

  @Override
  default Ast.Expression visitIn(final Ast.In expr) {
    return new Ast.In(visit(expr.lhs()), visit(expr.rhs()));
  }

  @Override
  default Ast.Expression visitLoop(final Ast.Loop expr) {
    return new Ast.Loop(visit(expr.body()));
  }

  @Override
  default Ast.Expression visitNegate(final Ast.Negate expr) {
    return new Ast.Negate(visit(expr.expression()));
  }

  @Override
  default Ast.Expression visitSpread(final Ast.Spread expr) {
    return new Ast.Spread(visit(expr.expression()));
  }

  @Override
  default Ast.Expression visitRest(final Ast.Rest expr) {
    return expr;
  }
}
