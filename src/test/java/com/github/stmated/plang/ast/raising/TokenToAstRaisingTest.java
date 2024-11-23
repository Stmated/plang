package com.github.stmated.plang.ast.raising;

import com.github.stmated.plang.Plang;
import com.github.stmated.plang.ast.Ast;
import com.github.stmated.plang.ast.AstVisitor;
import com.github.stmated.plang.ast.TokenToAstRaising;
import com.github.stmated.plang.lexer.PlangLexer;
import com.github.stmated.plang.lexer.PlangLexerSteps;
import com.github.stmated.plang.parser.PlangTestUtil;
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

    final var ibo = assertType(Ast.BinaryOperation.class, expressions[0]);

    final var lhs = assertType(Ast.Literal.class, ibo.lhs());
    final var rhs = assertType(Ast.Literal.class, ibo.rhs());

    Assertions.assertEquals("1", lhs.content());
    Assertions.assertEquals(Ast.BinaryOperationKind.ADD, ibo.kind());
    Assertions.assertEquals("1", rhs.content());
  }

  @Test
  @SneakyThrows
  void testOperatorPrecedence2() {

    final var program = this.parseProgram("a < 1 && b > 2");
    final var expressions = toExpressions(program.children());

    Assertions.assertNotNull(program);
    Assertions.assertEquals(1, expressions.length);

    final var ibo = assertType(Ast.BinaryOperation.class, expressions[0]);
    final var lhs = assertType(Ast.BinaryOperation.class, ibo.lhs());
    final var rhs = assertType(Ast.BinaryOperation.class, ibo.rhs());

    Assertions.assertEquals(Ast.BinaryOperationKind.AND, ibo.kind());
    Assertions.assertEquals(Ast.BinaryOperationKind.LT, lhs.kind());
    Assertions.assertEquals(Ast.BinaryOperationKind.GT, rhs.kind());
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
      case Ast.Program astProgram -> {
        var i = 0;
      }
      case Ast.BinaryOperation astBinaryOperation -> {
        var i = 0;
      }
      case Ast.MutabilityKind mutabilityKind -> {
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

    as(expressions[0], Ast.BinaryOperation.class, ibo -> {

      as(ibo.lhs(), Ast.BinaryOperation.class, lhs -> {
        Assertions.assertEquals(Ast.BinaryOperationKind.LT, lhs.kind());
      });

      as(ibo.rhs(), Ast.BinaryOperation.class, rhs -> {
        Assertions.assertEquals(Ast.BinaryOperationKind.OR, rhs.kind());

        as(rhs.lhs(), Ast.BinaryOperation.class, rhs_lhs -> {
          Assertions.assertEquals(Ast.BinaryOperationKind.GT, rhs_lhs.kind());
        });

        as(rhs.rhs(), Ast.BinaryOperation.class, rhs_rhs -> {
          Assertions.assertEquals(Ast.BinaryOperationKind.EQUALS, rhs_rhs.kind());
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

    as(expressions[0], Ast.BinaryOperation.class, ibo -> {

      as(ibo.lhs(), Ast.BinaryOperation.class, lhs -> {
        Assertions.assertEquals(Ast.BinaryOperationKind.LT, lhs.kind());
      });

      as(ibo.rhs(), Ast.BinaryOperation.class, rhs -> {
        Assertions.assertEquals(Ast.BinaryOperationKind.OR, rhs.kind());

        as(rhs.lhs(), Ast.BinaryOperation.class, rhs_lhs -> {
          Assertions.assertEquals(Ast.BinaryOperationKind.GT, rhs_lhs.kind());
        });

        as(rhs.rhs(), Ast.BinaryOperation.class, rhs_rhs -> {
          Assertions.assertEquals(Ast.BinaryOperationKind.EQUALS, rhs_rhs.kind());
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

    as(ast[0], Ast.Literal.class, literal -> {
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

    as(ast[0], Ast.Negate.class, negate -> {
      as(negate.expression(), Ast.Lexeme.class, id -> {
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
    as(ast[0], Ast.Literal.class, literal -> {

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
    as(ast[0],Ast.Call.class, call -> {
      as(call.target(), Ast.Paren.class, paren -> {
        as(paren.expression(), Ast.Callable.class, callable -> {
          as(callable.lhs(), Ast.Paren.class, call_lhs_paren -> {
            as(call_lhs_paren.expression(), Ast.Expressions.class, call_lhs_exprs -> {
              as(call_lhs_exprs.children()[0], Ast.Labeling.class, labeling -> {
                as(labeling.lhs(), Ast.Lexeme.class, id -> {
                  Assertions.assertEquals("a", id.name());
                });
                as(labeling.rhs(), Ast.Lexeme.class, id -> {
                  Assertions.assertEquals("int", id.name());
                });
              });
              as(call_lhs_exprs.children()[1], Ast.Labeling.class, labeling -> {
                as(labeling.lhs(), Ast.Lexeme.class, id -> {
                  Assertions.assertEquals("b", id.name());
                });
                as(labeling.rhs(), Ast.Lexeme.class, id -> {
                  Assertions.assertEquals("int", id.name());
                });
              });
            });
          });

          as(callable.rhs(), Ast.BinaryOperation.class, bop -> {
            Assertions.assertEquals(Ast.BinaryOperationKind.ADD, bop.kind());
          });
        });
      });

      as(call.paren(), Ast.Paren.class, paren -> {
        as(paren.expression(), Ast.Expressions.class, exprs -> {
          as(exprs.children()[0], Ast.Literal.class, literal -> Assertions.assertEquals("5", literal.content()));
          as(exprs.children()[1], Ast.Literal.class, literal -> Assertions.assertEquals("5", literal.content()));
        });
      });
    });


  }

  private Ast.Expression[] toExpressions(Ast.Expression expr) {

    if (expr instanceof Ast.Expressions exprs) {
      return exprs.children();
    } else if (expr instanceof Ast.Program program) {
      return toExpressions(program.children());
    } else {
      return new Ast.Expression[]{expr};
    }
  }

  private <T> void as(Ast.Expression exp, Class<T> clazz, Consumer<T> then) {

    Assertions.assertInstanceOf(clazz, exp);

    if (then != null) {
      then.accept((T) exp);
    }
  }

  private <T> T assertType(Class<T> clazz, Ast.Expression exp) {

    Assertions.assertInstanceOf(clazz, exp);
    return (T) exp;
  }

  private void isIdentifier(Ast.Expression exp, String expected) {

    Assertions.assertInstanceOf(Ast.Lexeme.class, exp);
    Assertions.assertEquals(expected, ((Ast.Lexeme) exp).name());
  }

  private void isLiteral(Ast.Expression exp, String expected) {

    Assertions.assertInstanceOf(Ast.Literal.class, exp);
    Assertions.assertEquals(expected, ((Ast.Literal) exp).content());
  }

  private <T, R> void is(Ast.Expression exp, Class<T> clazz, Function<T, R> mapper, Object expected) {

    Assertions.assertInstanceOf(clazz, exp);

    final var res = mapper.apply((T) exp);
    Assertions.assertEquals(expected, res);
  }

  @SneakyThrows
  private Ast.Program parseProgram(String code) {

    try (final var tokens = new PlangLexer(PlangTestUtil.stringToStream(code))) {
      final var parser = new TokenToAstRaising(tokens);

      return parser.parse();
    }
  }

  private static class ToStringTreeAstVisitor implements AstVisitor<String> {

    @Override
    public String visit(Ast.Expression expr) {

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
    public String visitLiteral(final Ast.Literal expr) {
      return "  " + expr.content() + "\n";
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
