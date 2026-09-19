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
import org.junit.jupiter.params.ParameterizedTest;
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

@Slf4j
class TokenToAstRaisingTest {

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
  void given__possible_fn_call_as_3_words__expect__lexemes() {
    final var ast = toExpressions(Inf.codeToAst("person eats fruit"));
    asAll(ast, Ast.Lexeme.class,
      expectLexeme("person"),
      expectLexeme("eats"),
      expectLexeme("fruit")
    );
  }

  @Test
  void given__possible_fn_call_as_parenthesised_3_words__expect__lexemes_and_paren() {
    final var ast = toExpressions(Inf.codeToAst("person eats(fruit)"));
    as(ast,
      Ast.Lexeme.class, expectLexeme("person"),
      Ast.PostfixExpression.class, pe -> as(
        pe.target(), Ast.Lexeme.class, expectLexeme("eats"),
        pe.suffix(), Ast.Paren.class, paren -> asAll(paren.expression(), Ast.Lexeme.class, expectLexeme("fruit"))
      ));
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
