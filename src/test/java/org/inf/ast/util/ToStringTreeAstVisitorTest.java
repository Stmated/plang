package org.inf.ast.util;

import org.inf.ast.Ast;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToStringTreeAstVisitorTest {

  @Test
  void rendersLexemeAsAnSExpressionWithQuotedSourceText() {

    final var node = new Ast.Lexeme("a");

    assertEquals("""
      (Lexeme "a")""".strip(), render(node));
  }

  @Test
  void rendersExpressionListsAsIndentedSExpressions() {

    final var node = new Ast.Expressions(new Ast.Expression[] {
      new Ast.Lexeme("a"),
      new Ast.Lexeme("b")
    });

    assertEquals("""
      (Expressions
        (Lexeme "a")
        (Lexeme "b"))""".strip(), render(node));
  }

  @Test
  void rendersBinaryOperatorsInTheNodeLabel() {

    final var node = new Ast.BinaryOperation(
      new Ast.Lexeme("left"),
      Ast.BinaryOperationKind.ADD,
      new Ast.Lexeme("right")
    );

    assertEquals("""
      (BinaryOperation ADD
        (Lexeme "left")
        (Lexeme "right"))""".strip(), render(node));
  }

  private static String render(final Ast.Expression node) {
    return new ToStringTreeAstVisitor().visit(node);
  }
}
