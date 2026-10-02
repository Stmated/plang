package org.inf.ast.passes.implicit_calls;

import org.inf.ast.Ast;
import org.inf.ast.AstTransformer;

import java.util.ArrayList;
import java.util.List;

/** Groups implicit calls and comma-separated lists in a raw AST that still retains separators. */
public class AstExpressionGroupingTransformer implements AstTransformer {

  public static Ast.Program pass(final Ast.Program program) {
    return new AstExpressionGroupingTransformer().visitAs(program, Ast.Program.class);
  }

  @Override
  public Ast.Expression visitExpressions(final Ast.Expressions expr) {
    return new Ast.Expressions(normalize(expr.children(), false));
  }

  @Override
  public Ast.Expression visitParen(final Ast.Paren expr) {
    return new Ast.Paren(normalizeGroup(expr.expression(), true));
  }

  @Override
  public Ast.Expression visitBlock(final Ast.Block expr) {
    return new Ast.Block(normalizeGroup(expr.expression(), false));
  }

  @Override
  public Ast.Expression visitBracket(final Ast.Bracket expr) {
    return new Ast.Bracket(normalize(expr.children(), true));
  }

  @Override
  public Ast.Expression visitNew(final Ast.New expr) {
    final var arguments = expr.arguments() instanceof Ast.Block block
      ? new Ast.Block(normalizeGroup(block.expression(), true))
      : visit(expr.arguments());
    return new Ast.New(visit(expr.target()), visitAs(expr.allocator(), Ast.Lexeme.class), arguments);
  }

  @Override
  public Ast.Expression visitMatch(final Ast.Match expr) {
    return new Ast.Match(
      visit(expr.target()),
      new Ast.Expressions(normalize(expr.children().children(), true))
    );
  }

  @Override
  public Ast.Expression visitComma(final Ast.Comma expr) {
    throw new IllegalArgumentException("Unexpected comma outside an implicit call");
  }

  private Ast.Expression normalizeGroup(final Ast.Expression expression, final boolean commaSeparated) {
    if (expression instanceof Ast.Expressions expressions) {
      return Ast.Expressions.from(List.of(normalize(expressions.children(), commaSeparated)));
    }
    if (commaSeparated && expression instanceof Ast.Comma) {
      return null;
    }
    return visit(expression);
  }

  private Ast.Expression[] normalize(final Ast.Expression[] children, final boolean commaSeparated) {
    final var cursor = new Cursor(this, children);
    final var result = new ArrayList<Ast.Expression>();
    while (cursor.hasNext()) {
      if (cursor.peek() instanceof Ast.Comma) {
        if (!commaSeparated) {
          throw new IllegalArgumentException("Unexpected comma outside an implicit call");
        }
        cursor.incrementIndex();
      } else {
        result.add(cursor.next());
      }
    }
    return result.toArray(new Ast.Expression[0]);
  }
}
