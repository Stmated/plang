package org.inf.ast.passes.implicit_calls;

import de.skuzzle.test.snapshots.junit5.EnableSnapshotTests;
import org.inf.Inf;
import org.inf.ast.Ast;
import org.inf.ast.util.ToStringTreeAstVisitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnableSnapshotTests
@Execution(ExecutionMode.SAME_THREAD)
public class AstExpressionGroupingTransformerTest {

  static Stream<Arguments> normalizationCases() {
    return Stream.of(
      Arguments.of(
        "f x + y, z;",
        "(Juxtaposition (Lexeme \"f\") (BinaryOperation ADD (Lexeme \"x\") (Lexeme \"y\")) (Lexeme \"z\")) (NoOp)"),

      Arguments.of(
        "f g x, y",
        "(Juxtaposition (Lexeme \"f\") (Juxtaposition (Lexeme \"g\") (Lexeme \"x\") (Lexeme \"y\")))"),

      Arguments.of(
        "obj.f x, y",
        "(Juxtaposition (DotAccess (Lexeme \"obj\") (Lexeme \"f\")) (Lexeme \"x\") (Lexeme \"y\"))"),

      Arguments.of(
        "Type::f x",
        "(Juxtaposition (StaticAccess (Lexeme \"Type\") (Lexeme \"f\")) (Lexeme \"x\"))"),

      Arguments.of(
        "f(x) y",
        "(Juxtaposition (PostfixExpression (Lexeme \"f\") (Paren (Lexeme \"x\"))) (Lexeme \"y\"))"),

      Arguments.of(
        "val r = f x, y;",
        "(Assignment (VariableDeclaration (Lexeme \"r\") Immutable false) (Juxtaposition (Lexeme \"f\") (Lexeme \"x\") (Lexeme \"y\"))) (NoOp)"),

      Arguments.of(
        "return f x, y",
        "(Return (Juxtaposition (Lexeme \"f\") (Lexeme \"x\") (Lexeme \"y\")))"),

      Arguments.of(
        "val fn = (x:int) => f x",
        "(Assignment (VariableDeclaration (Lexeme \"fn\") Immutable false) (Callable (Paren (Labeling (Lexeme \"x\") (Lexeme \"int\"))) (Juxtaposition (Lexeme \"f\") (Lexeme \"x\"))))"),

      Arguments.of(
        "then f x, y",
        "(Then (Juxtaposition (Lexeme \"f\") (Lexeme \"x\") (Lexeme \"y\")))"),

      Arguments.of(
        "yield f x, y",
        "(Yield (Juxtaposition (Lexeme \"f\") (Lexeme \"x\") (Lexeme \"y\")))"),

      Arguments.of(
        "outer(inner x, y)",
        "(PostfixExpression (Lexeme \"outer\") (Paren (Juxtaposition (Lexeme \"inner\") (Lexeme \"x\") (Lexeme \"y\"))))"),

      Arguments.of(
        "outer((inner x), y)",
        "(PostfixExpression (Lexeme \"outer\") (Paren (Expressions (Paren (Juxtaposition (Lexeme \"inner\") (Lexeme \"x\"))) (Comma) (Lexeme \"y\"))))"),

      Arguments.of(
        "outer(x, y)",
        "(PostfixExpression (Lexeme \"outer\") (Paren (Expressions (Lexeme \"x\") (Comma) (Lexeme \"y\"))))"),

      Arguments.of(
        "(x)",
        "(Paren (Lexeme \"x\"))"),

      Arguments.of(
        "(x,)",
        "(Paren (Expressions (Lexeme \"x\") (Comma)))"),

      Arguments.of(
        "(x, y)",
        "(Paren (Expressions (Lexeme \"x\") (Comma) (Lexeme \"y\")))"),

      Arguments.of(
        "(x, y,)",
        "(Paren (Expressions (Lexeme \"x\") (Comma) (Lexeme \"y\") (Comma)))"),

      Arguments.of(
        "((x,))",
        "(Paren (Paren (Expressions (Lexeme \"x\") (Comma))))"),

      Arguments.of(
        "((x,),)",
        "(Paren (Expressions (Paren (Expressions (Lexeme \"x\") (Comma))) (Comma)))"),

      Arguments.of(
        "((x, y), z)",
        "(Paren (Expressions (Paren (Expressions (Lexeme \"x\") (Comma) (Lexeme \"y\"))) (Comma) (Lexeme \"z\")))"),

      Arguments.of(
        "((x, y))",
        "(Paren (Paren (Expressions (Lexeme \"x\") (Comma) (Lexeme \"y\"))))"),

      Arguments.of(
        "outer((x, y))",
        "(PostfixExpression (Lexeme \"outer\") (Paren (Paren (Expressions (Lexeme \"x\") (Comma) (Lexeme \"y\")))))"),

      Arguments.of(
        "outer(x,)",
        "(PostfixExpression (Lexeme \"outer\") (Paren (Expressions (Lexeme \"x\") (Comma))))"),

      Arguments.of(
        "(x, inner y, z)",
        "(Paren (Expressions (Lexeme \"x\") (Comma) (Juxtaposition (Lexeme \"inner\") (Lexeme \"y\") (Lexeme \"z\"))))"),

      Arguments.of(
        "((inner x, y),)",
        "(Paren (Expressions (Paren (Juxtaposition (Lexeme \"inner\") (Lexeme \"x\") (Lexeme \"y\"))) (Comma)))"),

      Arguments.of(
        "([x, y])",
        "(Paren (Bracket (Lexeme \"x\") (Lexeme \"y\")))"),

      Arguments.of(
        "(x; y)",
        "(Paren (Expressions (Lexeme \"x\") (NoOp) (Lexeme \"y\")))"),

      Arguments.of(
        "(f x, y; g z)",
        "(Paren (Expressions (Juxtaposition (Lexeme \"f\") (Lexeme \"x\") (Lexeme \"y\")) (NoOp) (Juxtaposition (Lexeme \"g\") (Lexeme \"z\"))))"),

      Arguments.of(
        "(a: int, b: bool,) => a",
        "(Callable (Paren (Expressions (Labeling (Lexeme \"a\") (Lexeme \"int\")) (Comma) (Labeling (Lexeme \"b\") (Lexeme \"bool\")) (Comma))) (Lexeme \"a\"))"),

      Arguments.of(
        "(a: (int, bool)) => a",
        "(Callable (Paren (Labeling (Lexeme \"a\") (Paren (Expressions (Lexeme \"int\") (Comma) (Lexeme \"bool\"))))) (Lexeme \"a\"))"),

      Arguments.of(
        "f[x]",
        "(PostfixExpression (Lexeme \"f\") (Bracket (Lexeme \"x\")))"),

      Arguments.of(
        "t[0][1]",
        "(PostfixExpression (PostfixExpression (Lexeme \"t\") (Bracket (Literal \"0\"))) (Bracket (Literal \"1\")))"),

      Arguments.of(
        "t[0][1][2]",
        "(PostfixExpression (PostfixExpression (PostfixExpression (Lexeme \"t\") (Bracket (Literal \"0\"))) (Bracket (Literal \"1\"))) (Bracket (Literal \"2\")))"),

      Arguments.of(
        "f t[0][1]",
        "(Juxtaposition (Lexeme \"f\") (PostfixExpression (PostfixExpression (Lexeme \"t\") (Bracket (Literal \"0\"))) (Bracket (Literal \"1\"))))"),

      Arguments.of(
        "t[0][1] + 2",
        "(BinaryOperation ADD (PostfixExpression (PostfixExpression (Lexeme \"t\") (Bracket (Literal \"0\"))) (Bracket (Literal \"1\"))) (Literal \"2\"))"),

      Arguments.of(
        "f()",
        "(PostfixExpression (Lexeme \"f\") (Paren))"),

      Arguments.of(
        "new stack Point { x = 1, y = 2 }",
        "(New (Lexeme \"Point\") (Lexeme \"stack\") (Block (Expressions (Assignment (Lexeme \"x\") (Literal \"1\")) (Assignment (Lexeme \"y\") (Literal \"2\")))))"),

      Arguments.of(
        "match (x) { 1 => 2, 3 => 4 }",
        "(Match (Paren (Lexeme \"x\")) (Expressions (Callable (Literal \"1\") (Literal \"2\")) (Callable (Literal \"3\") (Literal \"4\"))))"),

      Arguments.of(
        "f 1; g 2;",
        "(Juxtaposition (Lexeme \"f\") (Literal \"1\")) (NoOp) (Juxtaposition (Lexeme \"g\") (Literal \"2\")) (NoOp)"),

      Arguments.of(
        "f",
        "(Lexeme \"f\")"),

      Arguments.of(
        "f - 1",
        "(BinaryOperation SUBTRACT (Lexeme \"f\") (Literal \"1\"))"),

      Arguments.of(
        "if p f x;",
        "(Conditional (Lexeme \"p\") (Lexeme \"f\")) (Lexeme \"x\") (NoOp)"),

      Arguments.of(
        "if (true) 1 2",
        "(Conditional (Paren (Literal \"true\")) (Literal \"1\")) (Literal \"2\")"),

      Arguments.of(
        "while p f x;",
        "(LoopWhile (Lexeme \"p\") (Lexeme \"f\")) (Lexeme \"x\") (NoOp)"),

      Arguments.of(
        "for p f x;",
        "(LoopFor (Lexeme \"p\") (Lexeme \"f\")) (Lexeme \"x\") (NoOp)"),

      Arguments.of(
        "do f p x;",
        "(LoopDoWhile (Lexeme \"f\") (Lexeme \"p\")) (Lexeme \"x\") (NoOp)"),

      Arguments.of(
        "if (pred x) then (f y) else { g z }",
        "(Conditional (Paren (Juxtaposition (Lexeme \"pred\") (Lexeme \"x\"))) (Then (Paren (Juxtaposition (Lexeme \"f\") (Lexeme \"y\")))) (Block (Juxtaposition (Lexeme \"g\") (Lexeme \"z\"))))"),

      Arguments.of(
        "for (a b; c d; e f) { g h }",
        "(LoopFor (Paren (Expressions (Juxtaposition (Lexeme \"a\") (Lexeme \"b\")) (NoOp) (Juxtaposition (Lexeme \"c\") (Lexeme \"d\")) (NoOp) (Juxtaposition (Lexeme \"e\") (Lexeme \"f\")))) (Block (Juxtaposition (Lexeme \"g\") (Lexeme \"h\"))))"),

      Arguments.of(
        "outer(f x; g y)",
        "(PostfixExpression (Lexeme \"outer\") (Paren (Expressions (Juxtaposition (Lexeme \"f\") (Lexeme \"x\")) (NoOp) (Juxtaposition (Lexeme \"g\") (Lexeme \"y\")))))"),

      Arguments.of(
        "[(f x), y]",
        "(Bracket (Paren (Juxtaposition (Lexeme \"f\") (Lexeme \"x\"))) (Lexeme \"y\"))"),

      Arguments.of(
        "[f x, y]",
        "(Bracket (Juxtaposition (Lexeme \"f\") (Lexeme \"x\") (Lexeme \"y\")))"),

      Arguments.of(
        "f 1, (g x), [y, z], -2",
        "(Juxtaposition (Lexeme \"f\") (Literal \"1\") (Paren (Juxtaposition (Lexeme \"g\") (Lexeme \"x\"))) (Bracket (Lexeme \"y\") (Lexeme \"z\")) (Literal \"-2\"))"),

      Arguments.of(
        "f + g x",
        "(BinaryOperation ADD (Lexeme \"f\") (Lexeme \"g\")) (Lexeme \"x\")"),

      Arguments.of(
        "f:int x",
        "(Labeling (Lexeme \"f\") (Lexeme \"int\")) (Lexeme \"x\")"),

      Arguments.of(
        "~(f x)",
        "(Partial (Paren (Juxtaposition (Lexeme \"f\") (Lexeme \"x\"))))"),

      Arguments.of(
        "~f x",
        "(Partial (Lexeme \"f\")) (Lexeme \"x\")"),

      Arguments.of(
        "const r = f x",
        "(Juxtaposition (Lexeme \"const\") (Assignment (Lexeme \"r\") (Juxtaposition (Lexeme \"f\") (Lexeme \"x\"))))")
    );
  }

  @ParameterizedTest
  @MethodSource("normalizationCases")
  void given__raw_ast__when__normalized__then__calls_are_grouped_without_mutating_input(
    final String code, final String expectedChildren
  ) {
    final var raw = Inf.codeToRawAst(code);
    final var printer = new ToStringTreeAstVisitor();
    final var rawBefore = printer.visit(raw);
    final var normalized = AstExpressionGroupingTransformer.pass(raw);
    final var normalizedTree = printer.visit(normalized);

    assertAll(
      () -> assertEquals("(Program (Expressions " + expectedChildren + "))", normalizedTree.replaceAll("\\s+", " ")),
      () -> assertEquals(rawBefore, printer.visit(raw)),
      () -> assertEquals(normalizedTree, printer.visit(AstExpressionGroupingTransformer.pass(raw)))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "f 1 2",
    "f 1,;",
    "f 1,",
    "outer(f 1,)",
    "f g 1 2",
    "f 1 val x = 2",
    "f x,,y",
    "x,y",
    "{ x,y }",
    "{ , }"
  })
  void given__malformed_arguments__when__normalized__then__rejected_without_mutating_input(final String code) {
    final var raw = Inf.codeToRawAst(code);
    final var printer = new ToStringTreeAstVisitor();
    final var before = printer.visit(raw);

    assertAll(
      () -> assertThrows(IllegalArgumentException.class, () -> AstExpressionGroupingTransformer.pass(raw)),
      () -> assertEquals(before, printer.visit(raw))
    );
  }

  @Test
  void given__normalized_arguments__when__visited__then__argument_boundaries_are_preserved() {
    final var raw = Inf.codeToRawAst("f");
    final var call = new Ast.Juxtaposition(
      ((Ast.Expressions) raw.children()).children()[0],
      new Ast.Expression[]{new Ast.Lexeme("x"), new Ast.Lexeme("y")}
    );
    final var printer = new ToStringTreeAstVisitor();
    final var before = printer.visit(call);

    assertEquals(before, printer.visit(new AstExpressionGroupingTransformer().visit(call)));
  }
}
