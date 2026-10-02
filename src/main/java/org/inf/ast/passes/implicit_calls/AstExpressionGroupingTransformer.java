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
    return new Ast.Expressions(normalize(expr.children(), CommaHandling.REJECT));
  }

  @Override
  public Ast.Expression visitParen(final Ast.Paren expr) {
    return new Ast.Paren(normalizeGroup(expr.expression(), CommaHandling.PRESERVE));
  }

  @Override
  public Ast.Expression visitBlock(final Ast.Block expr) {
    return new Ast.Block(normalizeGroup(expr.expression(), CommaHandling.REJECT));
  }

  @Override
  public Ast.Expression visitBracket(final Ast.Bracket expr) {
    return new Ast.Bracket(normalize(expr.children(), CommaHandling.DISCARD));
  }

  @Override
  public Ast.Expression visitNew(final Ast.New expr) {
    final var arguments = expr.arguments() instanceof Ast.Block block
      ? new Ast.Block(normalizeGroup(block.expression(), CommaHandling.DISCARD))
      : visit(expr.arguments());
    return new Ast.New(visit(expr.target()), visitAs(expr.allocator(), Ast.Lexeme.class), arguments);
  }

  @Override
  public Ast.Expression visitMatch(final Ast.Match expr) {
    return new Ast.Match(
      visit(expr.target()),
      new Ast.Expressions(normalize(expr.children().children(), CommaHandling.DISCARD))
    );
  }

  @Override
  public Ast.Expression visitComma(final Ast.Comma expr) {
    throw new IllegalArgumentException("Unexpected comma outside an implicit call");
  }

  private Ast.Expression normalizeGroup(final Ast.Expression expression, final CommaHandling commas) {
    if (expression instanceof Ast.Expressions expressions) {
      return Ast.Expressions.from(List.of(normalize(expressions.children(), commas)));
    }
    if (expression instanceof Ast.Comma && commas != CommaHandling.REJECT) {
      return commas == CommaHandling.PRESERVE ? expression : null;
    }
    return visit(expression);
  }

  private Ast.Expression[] normalize(final Ast.Expression[] children, final CommaHandling commas) {
    final var cursor = new Cursor(this, children);
    final var result = new ArrayList<Ast.Expression>();
    while (cursor.hasNext()) {
      if (cursor.peek() instanceof Ast.Comma) {
        if (commas == CommaHandling.REJECT) {
          throw new IllegalArgumentException("Unexpected comma outside an implicit call");
        }
        if (commas == CommaHandling.PRESERVE) {
          result.add(cursor.peek());
        }
        cursor.incrementIndex();
      } else {
        result.add(cursor.next());
      }
    }
    return result.toArray(new Ast.Expression[0]);
  }

  private enum CommaHandling {
    REJECT, DISCARD, PRESERVE
  }
}
