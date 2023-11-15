package com.github.stmated.plang.parser;

import com.github.stmated.plang.Plang;
import com.github.stmated.plang.ast.AstVisitor;
import com.github.stmated.plang.ast.model.AstBinaryOperation;
import com.github.stmated.plang.ast.model.AstBinaryOperationKind;
import com.github.stmated.plang.ast.model.AstCall;
import com.github.stmated.plang.ast.model.AstCallable;
import com.github.stmated.plang.ast.model.AstExpression;
import com.github.stmated.plang.ast.model.AstExpressions;
import com.github.stmated.plang.ast.model.AstIdentifier;
import com.github.stmated.plang.ast.model.AstLabeling;
import com.github.stmated.plang.ast.model.AstLiteral;
import com.github.stmated.plang.ast.model.AstMutabilityKind;
import com.github.stmated.plang.ast.model.AstNegate;
import com.github.stmated.plang.ast.model.AstParen;
import com.github.stmated.plang.ast.model.AstProgram;
import com.github.stmated.plang.lexer.PlangLexer;
import com.github.stmated.plang.lexer.PlangLexerSteps;
import com.github.stmated.plang.ty.Ty;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Slf4j
class TokenToAstRaisingTest {

  @Test
  void testParse() {

    final var program = this.parseProgram("1 + 1");
    final var expressions = toExpressions(program.children());

    Assertions.assertNotNull(program);
    Assertions.assertEquals(1, expressions.length);

    final var ibo = assertType(AstBinaryOperation.class, expressions[0]);

    final var lhs = assertType(AstLiteral.class, ibo.lhs());
    final var rhs = assertType(AstLiteral.class, ibo.rhs());

    Assertions.assertEquals("1", lhs.content());
    Assertions.assertEquals(AstBinaryOperationKind.ADD, ibo.kind());
    Assertions.assertEquals("1", rhs.content());
  }

  @Test
  @SneakyThrows
  void testOperatorPrecedence2() {

    final var program = this.parseProgram("a < 1 && b > 2");
    final var expressions = toExpressions(program.children());

    Assertions.assertNotNull(program);
    Assertions.assertEquals(1, expressions.length);

    final var ibo = assertType(AstBinaryOperation.class, expressions[0]);
    final var lhs = assertType(AstBinaryOperation.class, ibo.lhs());
    final var rhs = assertType(AstBinaryOperation.class, ibo.rhs());

    Assertions.assertEquals(AstBinaryOperationKind.AND, ibo.kind());
    Assertions.assertEquals(AstBinaryOperationKind.LT, lhs.kind());
    Assertions.assertEquals(AstBinaryOperationKind.GT, rhs.kind());
  }

  // TODO: Create tests that checks exact result of:
  //        * Result<(String, String), Error>
  //        * something<unit8>(2)

  @Test
  @SneakyThrows
  void testForLoop() {

    final var program = this.parseProgram("for (var i = 0; i < 10; i.increment()) { }");

    Object o = null;

    switch (o) {
      case AstProgram astProgram -> {
        var i = 0;
      }
      case AstBinaryOperation astBinaryOperation -> {
        var i = 0;
      }
      case AstMutabilityKind astMutabilityKind -> {
        var i = 0;
      }
      case null, default -> {
      }
    }
  }

  @Test
  @SneakyThrows
  void testOperatorPrecedence3() {

    final var program = this.parseProgram("a < 1 && b > 2 || x == 3");
    final var expressions = toExpressions(program.children());

    Assertions.assertNotNull(program);
    Assertions.assertEquals(1, expressions.length);

    as(expressions[0], AstBinaryOperation.class, ibo -> {

      as(ibo.lhs(), AstBinaryOperation.class, lhs -> {
        Assertions.assertEquals(AstBinaryOperationKind.LT, lhs.kind());
      });

      as(ibo.rhs(), AstBinaryOperation.class, rhs -> {
        Assertions.assertEquals(AstBinaryOperationKind.OR, rhs.kind());

        as(rhs.lhs(), AstBinaryOperation.class, rhs_lhs -> {
          Assertions.assertEquals(AstBinaryOperationKind.GT, rhs_lhs.kind());
        });

        as(rhs.rhs(), AstBinaryOperation.class, rhs_rhs -> {
          Assertions.assertEquals(AstBinaryOperationKind.EQUALS, rhs_rhs.kind());
        });
      });
    });
  }

  @Test
  @SneakyThrows
  void testImportExport() {

    final var program = this.parseProgram("a < 1 && b > 2 || x == 3");
    final var expressions = toExpressions(program.children());

    Assertions.assertNotNull(program);
    Assertions.assertEquals(1, expressions.length);

    as(expressions[0], AstBinaryOperation.class, ibo -> {

      as(ibo.lhs(), AstBinaryOperation.class, lhs -> {
        Assertions.assertEquals(AstBinaryOperationKind.LT, lhs.kind());
      });

      as(ibo.rhs(), AstBinaryOperation.class, rhs -> {
        Assertions.assertEquals(AstBinaryOperationKind.OR, rhs.kind());

        as(rhs.lhs(), AstBinaryOperation.class, rhs_lhs -> {
          Assertions.assertEquals(AstBinaryOperationKind.GT, rhs_lhs.kind());
        });

        as(rhs.rhs(), AstBinaryOperation.class, rhs_rhs -> {
          Assertions.assertEquals(AstBinaryOperationKind.EQUALS, rhs_rhs.kind());
        });
      });
    });
  }

  public static Stream<Arguments> allValidTestFiles() throws IOException {
    return PlangTestUtil.testShouldSucceedSource();
  }

  @ParameterizedTest
  @MethodSource("allValidTestFiles")
  @SneakyThrows
  void testAllFiles(Path path) {

    // Does not test real validity, just that it does not crash.

    final var steps = new PlangLexerSteps();

    try (final var tokens = new PlangLexer(Files.newInputStream(path))) {
      final var transformed = steps.transform(tokens);
      final var parser = new TokenToAstRaising(transformed);
      final var program = parser.parse();
      Assertions.assertNotNull(program);
    }
  }

  @Test
  @SneakyThrows
  void testGenerics() {

    final var pass2 = new PlangLexerSteps();
    final var path = Path.of("src/test/resources/plang/valid_parse/valid_generics.plang").toAbsolutePath();
    try (final var tokens = new PlangLexer(Files.newInputStream(path))) {
      final var transformed = pass2.transform(tokens);
      final var parser = new TokenToAstRaising(transformed);
      final var program = parser.parse();
      Assertions.assertNotNull(program);
    }
  }

  @Test
  @SneakyThrows
  void testIterate() {

    final var pass2 = new PlangLexerSteps();
    final var path = Path.of("src/test/resources/plang/valid_parse/valid_iterate.plang").toAbsolutePath();
    try (final var tokens = new PlangLexer(Files.newInputStream(path))) {
      final var transformed = pass2.transform(tokens);
      final var parser = new TokenToAstRaising(transformed);
      final var program = parser.parse();
      Assertions.assertNotNull(program);

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
  void testNegativeNumber(String code) {

    final var ast = toExpressions(Plang.codeToAst(code));

    as(ast[0], AstLiteral.class, literal -> {
      Assertions.assertEquals("-1", literal.content());
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "-something",
    " -something",
    " - something "
  })
  void testUnaryNegate(String code) {
    final var ast = toExpressions(Plang.codeToAst(code));

    as(ast[0], AstNegate.class, negate -> {
      as(negate.expression(), AstIdentifier.class, id -> {
        Assertions.assertEquals("something", id.name());
      });
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "+1",
    " +1",
    " + 1 "
  })
  void testPositiveNumber(String code) {
    final var ast = toExpressions(Plang.codeToAst(code));
    as(ast[0], AstLiteral.class, literal -> {

      Assertions.assertEquals("1", literal.content());
      Assertions.assertEquals(Ty.INTEGER, literal.ty());
    });
  }

  @Test
  void testAnonymousFnWithDirectCall() {

    final var ast = toExpressions(Plang.codeToAst("((a: int, b: int) => a + b)(5, 5)"));

    // TODO: Fix test case :) And make sure all tests still work properly
    //        ... is it worth looking into making a function call a binary operation? Would that make it easier to parse and still understandable?

    Assertions.assertEquals(1, ast.length);

    // TODO: Convert this into some common format that is common in lang dev -- need to find some known format
    //        So we can easily compare against a string, and make it understandable for others
    as(ast[0], AstCall.class, call -> {
      as(call.target(), AstParen.class, paren -> {
        as(paren.expression(), AstCallable.class, callable -> {
          as(callable.lhs(), AstParen.class, call_lhs_paren -> {
            as(call_lhs_paren.expression(), AstExpressions.class, call_lhs_exprs -> {
              as(call_lhs_exprs.children()[0], AstLabeling.class, labeling -> {
                as(labeling.lhs(), AstIdentifier.class, id -> {
                  Assertions.assertEquals("a", id.name());
                });
                as(labeling.rhs(), AstIdentifier.class, id -> {
                  Assertions.assertEquals("int", id.name());
                });
              });
              as(call_lhs_exprs.children()[1], AstLabeling.class, labeling -> {
                as(labeling.lhs(), AstIdentifier.class, id -> {
                  Assertions.assertEquals("b", id.name());
                });
                as(labeling.rhs(), AstIdentifier.class, id -> {
                  Assertions.assertEquals("int", id.name());
                });
              });
            });
          });

          as(callable.rhs(), AstBinaryOperation.class, bop -> {
            Assertions.assertEquals(AstBinaryOperationKind.ADD, bop.kind());
          });
        });
      });

      as(call.paren(), AstParen.class, paren -> {
        as(paren.expression(), AstExpressions.class, exprs -> {
          as(exprs.children()[0], AstLiteral.class, literal -> Assertions.assertEquals("5", literal.content()));
          as(exprs.children()[1], AstLiteral.class, literal -> Assertions.assertEquals("5", literal.content()));
        });
      });
    });


  }

  private AstExpression[] toExpressions(AstExpression expr) {

    if (expr instanceof AstExpressions exprs) {
      return exprs.children();
    } else if (expr instanceof AstProgram program) {
      return toExpressions(program.children());
    } else {
      return new AstExpression[]{expr};
    }
  }

  private <T> void as(AstExpression exp, Class<T> clazz, Consumer<T> then) {

    Assertions.assertInstanceOf(clazz, exp);

    if (then != null) {
      then.accept((T) exp);
    }
  }

  private <T> T assertType(Class<T> clazz, AstExpression exp) {

    Assertions.assertInstanceOf(clazz, exp);
    return (T) exp;
  }

  private void isIdentifier(AstExpression exp, String expected) {

    Assertions.assertInstanceOf(AstIdentifier.class, exp);
    Assertions.assertEquals(expected, ((AstIdentifier) exp).name());
  }

  private void isLiteral(AstExpression exp, String expected) {

    Assertions.assertInstanceOf(AstLiteral.class, exp);
    Assertions.assertEquals(expected, ((AstLiteral) exp).content());
  }

  private <T, R> void is(AstExpression exp, Class<T> clazz, Function<T, R> mapper, Object expected) {

    Assertions.assertInstanceOf(clazz, exp);

    final var res = mapper.apply((T) exp);
    Assertions.assertEquals(expected, res);
  }

  @SneakyThrows
  private AstProgram parseProgram(String code) {

    try (final var tokens = new PlangLexer(PlangTestUtil.stringToStream(code))) {
      final var parser = new TokenToAstRaising(tokens);

      return parser.parse();
    }
  }

  private static class ToStringTreeAstVisitor implements AstVisitor<String> {

    @Override
    public String visit(AstExpression expr) {

      if (expr == null) {
        return this.noValue();
      }

      final var className = expr.getClass().getSimpleName();
      final var fixedClassName = className.replace("Initial", "");

      final var visited = AstVisitor.super.visit(expr);

      final var currentIndent = "  ";
      final var indented = visited.replace("\n", "\n" + currentIndent);

      return fixedClassName + "\n" + indented;
    }

    @Override
    public String visitLiteral(final AstLiteral expr) {
      return STR."  \{expr.content()}\n";
    }

    @Override
    public String aggregate(String a, String b) {
      return a + b;
    }

    @Override
    public String noValue() {
      return "";
    }
  }
}
