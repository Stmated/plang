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
  void given__consecutive_brackets__when__parsed__then__nested_postfix_access() {
    final var expressions = toExpressions(Inf.codeToRawAst("t[0][1]"));
    assertEquals(1, expressions.length);
    final var outer = assertType(Ast.PostfixExpression.class, expressions[0]);
    final var inner = assertType(Ast.PostfixExpression.class, outer.target());
    final var first = assertType(Ast.Bracket.class, inner.suffix());
    final var second = assertType(Ast.Bracket.class, outer.suffix());
    Assertions.assertAll(
      () -> assertEquals("t", assertType(Ast.Lexeme.class, inner.target()).name()),
      () -> assertEquals("0", assertType(Ast.Literal.class, first.children()[0]).content()),
      () -> assertEquals("1", assertType(Ast.Literal.class, second.children()[0]).content())
    );
  }

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

  @ParameterizedTest
  @CsvSource({
    "val, Immutable",
    "var, Mutable"
  })
  void given__union_type_annotation__when__parsed__then__assignment_lhs_is_declaration(
    final String keyword, final Ast.MutabilityKind mutability
  ) {
    final var code = "%s v: A | B = other".formatted(keyword);
    final var program = Inf.codeToRawAst(code);
    final var expressions = toExpressions(program);
    assertEquals(1, expressions.length);
    final var assignment = assertType(Ast.Assignment.class, expressions[0]);
    final var declaration = assertType(Ast.VariableDeclaration.class, assignment.lhs());
    final var union = assertType(Ast.BinaryOperation.class, declaration.type());
    Assertions.assertAll(
      () -> assertEquals(new Ast.Lexeme("v"), declaration.lexeme()),
      () -> assertEquals(mutability, declaration.mutabilityKind()),
      () -> assertEquals(Ast.BinaryOperationKind.BIT_OR, union.kind()),
      () -> assertEquals(new Ast.Lexeme("A"), union.lhs()),
      () -> assertEquals(new Ast.Lexeme("B"), union.rhs()),
      () -> assertEquals(new Ast.Lexeme("other"), assignment.rhs()),
      () -> assertEquals(program, Inf.codeToAst(code))
    );
  }

  @ParameterizedTest
  @CsvSource(value = {
    "A | B | C # (BinaryOperation BIT_OR (Lexeme \"A\") (BinaryOperation BIT_OR (Lexeme \"B\") (Lexeme \"C\")))",
    "(A | B) # (Paren (BinaryOperation BIT_OR (Lexeme \"A\") (Lexeme \"B\")))",
    "(A) | (B) # (BinaryOperation BIT_OR (Paren (Lexeme \"A\")) (Paren (Lexeme \"B\")))",
    "A | (B | C) # (BinaryOperation BIT_OR (Lexeme \"A\") (Paren (BinaryOperation BIT_OR (Lexeme \"B\") (Lexeme \"C\"))))",
    "ns.A | ns.B # (BinaryOperation BIT_OR (DotAccess (Lexeme \"ns\") (Lexeme \"A\")) (DotAccess (Lexeme \"ns\") (Lexeme \"B\")))"
  }, delimiter = '#')
  void given__compound_union_annotation__when__parsed__then__whole_annotation_belongs_to_declaration(
    final String annotation, final String expected
  ) {
    final var expressions = toExpressions(Inf.codeToRawAst("val v: %s = other".formatted(annotation)));
    assertEquals(1, expressions.length);
    final var assignment = assertType(Ast.Assignment.class, expressions[0]);
    final var declaration = assertType(Ast.VariableDeclaration.class, assignment.lhs());
    Assertions.assertAll(
      () -> assertEquals(expected, new ToStringTreeAstVisitor().visit(declaration.type()).replaceAll("\\s+", " ")),
      () -> assertEquals(new Ast.Lexeme("other"), assignment.rhs())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val v: A | B",
    "var v: A | B",
    "val v: A | B;",
    "var v: A | B;"
  })
  void given__union_annotation_without_initializer__when__parsed__then__declaration_owns_union(final String code) {
    final var expressions = toExpressions(Inf.codeToRawAst(code));
    assertEquals(code.endsWith(";") ? 2 : 1, expressions.length);
    final var declaration = assertType(Ast.VariableDeclaration.class, expressions[0]);
    final var union = assertType(Ast.BinaryOperation.class, declaration.type());
    Assertions.assertAll(
      () -> assertEquals(Ast.BinaryOperationKind.BIT_OR, union.kind()),
      () -> assertEquals(new Ast.Lexeme("A"), union.lhs()),
      () -> assertEquals(new Ast.Lexeme("B"), union.rhs())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val v: A | B = x | y",
    "val v: A = x | y",
    "val v = x | y"
  })
  void given__bitwise_or_initializer__when__parsed__then__bitwise_or_remains_on_assignment_rhs(final String code) {
    final var expressions = toExpressions(Inf.codeToRawAst(code));
    assertEquals(1, expressions.length);
    final var assignment = assertType(Ast.Assignment.class, expressions[0]);
    assertType(Ast.VariableDeclaration.class, assignment.lhs());
    final var operation = assertType(Ast.BinaryOperation.class, assignment.rhs());
    Assertions.assertAll(
      () -> assertEquals(Ast.BinaryOperationKind.BIT_OR, operation.kind()),
      () -> assertEquals(new Ast.Lexeme("x"), operation.lhs()),
      () -> assertEquals(new Ast.Lexeme("y"), operation.rhs())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val v: A |",
    "val v: A | = other",
    "val v: | B = other"
  })
  void given__missing_union_operand__when__parsed__then__error(final String code) {
    assertThrows(RuntimeException.class, () -> Inf.codeToRawAst(code));
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
}
