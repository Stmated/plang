package org.inf.ast.raising;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.inf.Inf;
import org.inf.ast.Ast;
import org.inf.ast.TokenToAstRaising;
import org.inf.ast.util.ToStringTreeAstVisitor;
import org.inf.lexer.InfLexer;
import org.inf.lexer.InfLexerSteps;
import org.inf.ty.Ty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.inf.ast.util.AstTestUtils.as;
import static org.inf.ast.util.AstTestUtils.asAll;
import static org.inf.ast.util.AstTestUtils.assertType;
import static org.inf.ast.util.AstTestUtils.expectLexeme;
import static org.inf.ast.util.AstTestUtils.parseProgram;
import static org.inf.ast.util.AstTestUtils.path;
import static org.inf.ast.util.AstTestUtils.toExpressions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
class TokenToAstRaisingTest {

  @Test
  void given__undelimited_conditional_call__when__parsed__then__rejected() {
    assertThrows(RuntimeException.class, () -> Inf.codeToRawAst("if p f x else g y"));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "f /* comment */ (x) | f(x)",
    "f x /* before operator */ + /* after operator */ y, z | f x + y, z",
    "val /* name */ r = /* value */ f x, y; | val r = f x, y;",
    "if /* predicate */ (p x) then /* branch */ (f y) else { g z } | if (p x) then (f y) else { g z }",
    "for (var i = 0; /* condition */ i < 3; i += 1) { f i } | for (var i = 0; i < 3; i += 1) { f i }",
    "[1, /* element */ 2] | [1, 2]"
  }, delimiter = '|')
  void given__comments_in_syntax__when__parsed__then__same_raw_and_normalized_structure(final String commented, final String plain) {
    final var printer = new ToStringTreeAstVisitor();
    Assertions.assertAll(
      () -> assertEquals(printer.visit(Inf.codeToRawAst(plain)), printer.visit(Inf.codeToRawAst(commented))),
      () -> assertEquals(printer.visit(Inf.codeToAst(plain)), printer.visit(Inf.codeToAst(commented)))
    );
  }

  @Test
  void testParse() {

    final var program = parseProgram("1 + 1");
    final var expressions = toExpressions(program.children());

    assertNotNull(program);
    assertEquals(1, expressions.length);

    final var ibo = assertType(Ast.BinaryOperation.class, expressions[0]);

    final var lhs = assertType(Ast.Literal.class, ibo.lhs());
    final var rhs = assertType(Ast.Literal.class, ibo.rhs());

    assertEquals("1", lhs.content());
    assertEquals(Ast.BinaryOperationKind.ADD, ibo.kind());
    assertEquals("1", rhs.content());
  }

  @Test
  @SneakyThrows
  void testOperatorPrecedence2() {

    final var program = parseProgram("a < 1 && b > 2");
    final var expressions = toExpressions(program.children());

    assertNotNull(program);
    assertEquals(1, expressions.length);

    final var ibo = assertType(Ast.BinaryOperation.class, expressions[0]);
    final var lhs = assertType(Ast.BinaryOperation.class, ibo.lhs());
    final var rhs = assertType(Ast.BinaryOperation.class, ibo.rhs());

    assertEquals(Ast.BinaryOperationKind.AND, ibo.kind());
    assertEquals(Ast.BinaryOperationKind.LT, lhs.kind());
    assertEquals(Ast.BinaryOperationKind.GT, rhs.kind());
  }

  @Test
  @SneakyThrows
  void testForLoop() {

    // TODO: Make this a snapshot test, where the input is this script and the output is a structured string which shows the tree structure
    final var program = parseProgram("for (var i = 0; i < 10; i.increment()) { }");

    assertNotNull(program);
  }

  @Test
  @SneakyThrows
  void testOperatorPrecedence3() {

    final var program = parseProgram("a < 1 && b > 2 || x == 3");
    final var expressions = toExpressions(program.children());

    assertNotNull(program);
    assertEquals(1, expressions.length);

    as(expressions[0], Ast.BinaryOperation.class, ibo -> {

      as(ibo.lhs(), Ast.BinaryOperation.class, lhs -> assertEquals(Ast.BinaryOperationKind.LT, lhs.kind()));

      as(ibo.rhs(), Ast.BinaryOperation.class, rhs -> {
        assertEquals(Ast.BinaryOperationKind.OR, rhs.kind());
        as(rhs.lhs(), Ast.BinaryOperation.class, rhs_lhs -> assertEquals(Ast.BinaryOperationKind.GT, rhs_lhs.kind()));
        as(rhs.rhs(), Ast.BinaryOperation.class, rhs_rhs -> assertEquals(Ast.BinaryOperationKind.EQUALS, rhs_rhs.kind()));
      });
    });
  }

  @Test
  @SneakyThrows
  void testImportExport() {

    final var program = parseProgram("a < 1 && b > 2 || x == 3");
    final var expressions = toExpressions(program.children());

    assertNotNull(program);
    assertEquals(1, expressions.length);

    as(expressions[0], Ast.BinaryOperation.class, ibo -> {

      as(ibo.lhs(), Ast.BinaryOperation.class, lhs -> assertEquals(Ast.BinaryOperationKind.LT, lhs.kind()));

      as(ibo.rhs(), Ast.BinaryOperation.class, rhs -> {
        assertEquals(Ast.BinaryOperationKind.OR, rhs.kind());

        as(rhs.lhs(), Ast.BinaryOperation.class, rhs_lhs -> assertEquals(Ast.BinaryOperationKind.GT, rhs_lhs.kind()));
        as(rhs.rhs(), Ast.BinaryOperation.class, rhs_rhs -> assertEquals(Ast.BinaryOperationKind.EQUALS, rhs_rhs.kind()));
      });
    });
  }

  @Test
  @SneakyThrows
  void testGenerics() {

    final var pass2 = new InfLexerSteps();
    final var path = Path.of("src/test/resources/inf/valid_parse/valid_generics.inf").toAbsolutePath();
    try (final var tokens = new InfLexer(Files.newInputStream(path))) {
      final var transformed = pass2.transform(tokens);
      final var parser = new TokenToAstRaising(transformed);
      final var program = parser.parse();
      assertNotNull(program);
    }
  }

  @Test
  @SneakyThrows
  void testIterate() {

    final var pass2 = new InfLexerSteps();
    final var path = Path.of("src/test/resources/inf/valid_parse/valid_iterate.inf").toAbsolutePath();
    try (final var tokens = new InfLexer(Files.newInputStream(path))) {
      final var transformed = pass2.transform(tokens);
      final var parser = new TokenToAstRaising(transformed);
      final var program = parser.parse();
      assertNotNull(program);

      final var treePrintVisitor = new ToStringTreeAstVisitor();
      treePrintVisitor.visit(program);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "-1",
    " -1",
    " - 1 "
  })
  void testNegativeNumber(final String code) {

    final var ast = toExpressions(Inf.codeToAst(code));

    as(ast[0], Ast.Literal.class, literal -> assertEquals("-1", literal.content()));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "-something",
    " -something",
    " - something "
  })
  void testUnaryNegate(final String code) {
    final var ast = toExpressions(Inf.codeToAst(code));

    as(ast[0], Ast.Negate.class, negate -> {
      as(negate.expression(), Ast.Lexeme.class, id -> assertEquals("something", id.name()));
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "+1",
    " +1",
    " + 1 "
  })
  void testPositiveNumber(final String code) {
    final var ast = toExpressions(Inf.codeToAst(code));
    asAll(ast, Ast.Literal.class, literal -> {
      assertEquals("1", literal.content());
      assertEquals(Ty.INTEGER, literal.ty());
    });
  }

  @Test
  void given__three_words__when__parsed__then__applications_nest_right() {
    final var ast = toExpressions(Inf.codeToAst("person eats fruit"));
    final var outer = Assertions.assertInstanceOf(Ast.Juxtaposition.class, ast[0]);
    final var inner = Assertions.assertInstanceOf(Ast.Juxtaposition.class, outer.arguments()[0]);
    Assertions.assertAll(
      () -> assertEquals(1, ast.length),
      () -> assertEquals(new Ast.Lexeme("person"), outer.target()),
      () -> assertEquals(new Ast.Lexeme("eats"), inner.target()),
      () -> Assertions.assertArrayEquals(new Ast.Expression[]{new Ast.Lexeme("fruit")}, inner.arguments())
    );
  }

  @Test
  void given__explicit_call_argument__when__parsed__then__outer_application_wraps_it() {
    final var ast = toExpressions(Inf.codeToAst("person eats(fruit)"));
    final var application = Assertions.assertInstanceOf(Ast.Juxtaposition.class, ast[0]);
    assertEquals(new Ast.Lexeme("person"), application.target());
    as(application.arguments(),
      Ast.PostfixExpression.class, pe -> as(
        pe.target(), Ast.Lexeme.class, expectLexeme("eats"),
        pe.suffix(), Ast.Paren.class, paren -> asAll(paren.expression(), Ast.Lexeme.class, expectLexeme("fruit"))
      ));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "f x | 1",
    "f x, y | 2",
    "f x + y, z * 2 | 2",
    "f x, -2 | 2",
    "f x, [1, 2] | 2"
  }, delimiter = '|')
  void given__implicit_call__when__parsed__then__arguments_are_grouped(final String code, final int count) {
    final var expressions = toExpressions(Inf.codeToAst(code));
    final var call = Assertions.assertInstanceOf(Ast.Juxtaposition.class, expressions[0]);
    Assertions.assertAll(
      () -> assertEquals(1, expressions.length),
      () -> assertEquals(new Ast.Lexeme("f"), call.target()),
      () -> assertEquals(count, call.arguments().length)
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "f x,\ny",
    "f\nx, y",
    "f /* comment */ x, y",
    "f x // comment\n, y"
  })
  void given__whitespace_or_comments__when__parsed__then__grouping_is_unchanged(final String code) {
    assertEquals(Inf.codeToAst("f x, y"), Inf.codeToAst(code));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "f 1,",
    "f 1,;",
    "outer(f 1,)",
    "f 1 2",
    "f 1 g 2"
  })
  void given__malformed_implicit_arguments__when__parsed__then__error(final String code) {
    Assertions.assertThrows(RuntimeException.class, () -> Inf.codeToAst(code));
  }

  @Test
  void given__semicolon__when__parsed__then__calls_are_separate() {
    final var expressions = toExpressions(Inf.codeToAst("f x; g y"));
    Assertions.assertAll(
      () -> assertEquals(3, expressions.length),
      () -> Assertions.assertInstanceOf(Ast.Juxtaposition.class, expressions[0]),
      () -> Assertions.assertInstanceOf(Ast.NoOp.class, expressions[1]),
      () -> Assertions.assertInstanceOf(Ast.Juxtaposition.class, expressions[2])
    );
  }

  @Test
  void given__possible_fn_call_as_member_specific_parenthesised_3_words__expect__lexemes_and_access_and_paren() {
    final var ast = toExpressions(Inf.codeToAst("person.eats(fruit)"));

    path(ast[0], Ast.DotAccess.class, Ast.DotAccess::rhs, Ast.PostfixExpression.class, Ast.PostfixExpression::suffix, Ast.Paren.class, (access, post, paren) -> {
      as(access.lhs(), post.target(), paren.expression(), Ast.Lexeme.class,
        expectLexeme("person"),
        expectLexeme("eats"),
        expectLexeme("fruit")
      );
    });
  }

//  @Test
//  void testAnonymousFnWithDirectCall() {
//
//    final var ast = toExpressions(Inf.codeToAst("((a: int, b: int) => a + b)(5, 5)"));
//
//    // TODO: Fix test case :) And make sure all tests still work properly
//    //        ... is it worth looking into making a function call a binary operation? Would that make it easier to parse and still understandable?
//
//    Assertions.assertEquals(1, ast.length);
//
//    // TODO: Convert this into some common format that is common in lang dev -- need to find some known format
//    //        So we can easily compare against a string, and make it understandable for others
//    as(ast[0], Ast.Call.class, call -> {
//      as(call.target(), Ast.Paren.class, paren -> {
//        as(paren.expression(), Ast.Callable.class, callable -> {
//          as(callable.lhs(), Ast.Paren.class, call_lhs_paren -> {
//            as(call_lhs_paren.expression(), Ast.Expressions.class, call_lhs_exprs -> {
//              as(call_lhs_exprs.children()[0], Ast.Labeling.class, labeling -> {
//                as(labeling.lhs(), Ast.Lexeme.class, id -> {
//                  Assertions.assertEquals("a", id.name());
//                });
//                as(labeling.rhs(), Ast.Lexeme.class, id -> {
//                  Assertions.assertEquals("int", id.name());
//                });
//              });
//              as(call_lhs_exprs.children()[1], Ast.Labeling.class, labeling -> {
//                as(labeling.lhs(), Ast.Lexeme.class, id -> {
//                  Assertions.assertEquals("b", id.name());
//                });
//                as(labeling.rhs(), Ast.Lexeme.class, id -> {
//                  Assertions.assertEquals("int", id.name());
//                });
//              });
//            });
//          });
//
//          as(callable.rhs(), Ast.BinaryOperation.class, bop -> {
//            Assertions.assertEquals(Ast.BinaryOperationKind.ADD, bop.kind());
//          });
//        });
//      });
//
//      as(call.paren(), Ast.Paren.class, paren -> {
//        as(paren.expression(), Ast.Expressions.class, exprs -> {
//          as(exprs.children()[0], Ast.Literal.class, literal -> Assertions.assertEquals("5", literal.content()));
//          as(exprs.children()[1], Ast.Literal.class, literal -> Assertions.assertEquals("5", literal.content()));
//        });
//      });
//    });
//  }
}
