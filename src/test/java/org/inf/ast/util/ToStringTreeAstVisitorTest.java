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

  @Test
  void rendersManyExpressionsCorrectly() {

    final var node = new Ast.Program(
      new Ast.Expressions(
        new Ast.Expression[] {
          new Ast.Comment("a"),
          new Ast.Comment("b"),
          new Ast.Comment("c"),
          new Ast.Comment("d"),
          new Ast.Comment("e"),
          new Ast.Comment("f")
        }
      )
    );

    assertEquals("""
      (Program
        (Expressions
          (Comment "a")
          (Comment "b")
          (Comment "c")
          (Comment "d")
          (Comment "e")
          (Comment "f")))""".strip(), render(node));
  }

  @Test
  void rendersEmptyExpressionListsWithoutChildren() {

    final var node = new Ast.Expressions(new Ast.Expression[0]);

    assertEquals("(Expressions)", render(node));
  }

  private static String render(final Ast.Expression node) {
    return new ToStringTreeAstVisitor().visit(node);
  }
}
