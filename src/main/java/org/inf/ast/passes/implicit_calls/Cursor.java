package org.inf.ast.passes.implicit_calls;

import java.util.ArrayList;
import org.inf.ast.Ast;

final class Cursor {

  private static final ArgumentStartVisitor ARGUMENT_START = new ArgumentStartVisitor();

  private final AstExpressionGroupingTransformer transformer;
  private final Ast.Expression[] children;
  private int index;

  Cursor(AstExpressionGroupingTransformer transformer, final Ast.Expression[] children) {
    this.transformer = transformer;
    this.children = children;
  }

  boolean hasNext() {
    return index < children.length;
  }

  Ast.Expression peek() {
    return hasNext() ? children[index] : null;
  }

  Ast.Expression next() {
    return extend(children[index++]);
  }

  void incrementIndex() {
    index++;
  }

  Ast.Expression extend(final Ast.Expression expression) {
    if (expression == null) {
      return null;
    }
    final var target = expression.visit(new TailTransformer(transformer, this));
    if (!canCall(target) || ARGUMENT_START.visit(peek()) != ArgumentStart.FIRST) {
      return target;
    }

    final var arguments = new ArrayList<Ast.Expression>();
    arguments.add(next());
    while (peek() instanceof Ast.Comma) {
      index++;
      if (ARGUMENT_START.visit(peek()) == ArgumentStart.NONE) {
        throw new IllegalArgumentException("Expected an implicit-call argument after comma");
      }
      arguments.add(next());
    }
    if (hasNext() && !(peek() instanceof Ast.NoOp)) {
      throw new IllegalArgumentException("Expected comma or semicolon after implicit-call argument");
    }
    return new Ast.Juxtaposition(target, arguments.toArray(new Ast.Expression[0]));
  }

  static boolean canCall(final Ast.Expression expression) {
    return expression instanceof Ast.Lexeme
      || expression instanceof Ast.DotAccess
      || expression instanceof Ast.StaticAccess
      || expression instanceof Ast.PostfixExpression
      || expression instanceof Ast.Paren;
  }
}
