package com.github.stmated.plang.ast.raising;

import com.github.stmated.plang.Plang;
import com.github.stmated.plang.ast.Ast.BinaryOperation;
import com.github.stmated.plang.ast.Ast.Callable;
import com.github.stmated.plang.ast.Ast.Expression;
import com.github.stmated.plang.ast.Ast.Identifier;
import com.github.stmated.plang.ast.Ast.Labeling;
import com.github.stmated.plang.ast.Ast.Literal;
import com.github.stmated.plang.ast.Ast.Paren;
import com.github.stmated.plang.ast.Ast.Program;
import com.github.stmated.plang.ast.AstVisitor;
import com.github.stmated.plang.ast.Ast.BinaryOperationKind;
import com.github.stmated.plang.ast.Ast.Call;
import com.github.stmated.plang.ast.Ast.Expressions;
import com.github.stmated.plang.ast.Ast.MutabilityKind;
import com.github.stmated.plang.ast.Ast.Negate;
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

    final var ibo = assertType(BinaryOperation.class, expressions[0]);

    final var lhs = assertType(Literal.class, ibo.lhs());
    final var rhs = assertType(Literal.class, ibo.rhs());

    Assertions.assertEquals("1", lhs.content());
    Assertions.assertEquals(BinaryOperationKind.ADD, ibo.kind());
    Assertions.assertEquals("1", rhs.content());
  }

  @Test
  @SneakyThrows
  void testOperatorPrecedence2() {

    final var program = this.parseProgram("a < 1 && b > 2");
    final var expressions = toExpressions(program.children());

    Assertions.assertNotNull(program);
    Assertions.assertEquals(1, expressions.length);

    final var ibo = assertType(BinaryOperation.class, expressions[0]);
    final var lhs = assertType(BinaryOperation.class, ibo.lhs());
    final var rhs = assertType(BinaryOperation.class, ibo.rhs());

    Assertions.assertEquals(BinaryOperationKind.AND, ibo.kind());
    Assertions.assertEquals(BinaryOperationKind.LT, lhs.kind());
    Assertions.assertEquals(BinaryOperationKind.GT, rhs.kind());
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
      case Program astProgram -> {
        var i = 0;
      }
      case BinaryOperation astBinaryOperation -> {
        var i = 0;
      }
      case MutabilityKind mutabilityKind -> {
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

    as(expressions[0], BinaryOperation.class, ibo -> {

      as(ibo.lhs(), BinaryOperation.class, lhs -> {
        Assertions.assertEquals(BinaryOperationKind.LT, lhs.kind());
      });

      as(ibo.rhs(), BinaryOperation.class, rhs -> {
        Assertions.assertEquals(BinaryOperationKind.OR, rhs.kind());

        as(rhs.lhs(), BinaryOperation.class, rhs_lhs -> {
          Assertions.assertEquals(BinaryOperationKind.GT, rhs_lhs.kind());
        });

        as(rhs.rhs(), BinaryOperation.class, rhs_rhs -> {
          Assertions.assertEquals(BinaryOperationKind.EQUALS, rhs_rhs.kind());
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

    as(expressions[0], BinaryOperation.class, ibo -> {

      as(ibo.lhs(), BinaryOperation.class, lhs -> {
        Assertions.assertEquals(BinaryOperationKind.LT, lhs.kind());
      });

      as(ibo.rhs(), BinaryOperation.class, rhs -> {
        Assertions.assertEquals(BinaryOperationKind.OR, rhs.kind());

        as(rhs.lhs(), BinaryOperation.class, rhs_lhs -> {
          Assertions.assertEquals(BinaryOperationKind.GT, rhs_lhs.kind());
        });

        as(rhs.rhs(), BinaryOperation.class, rhs_rhs -> {
          Assertions.assertEquals(BinaryOperationKind.EQUALS, rhs_rhs.kind());
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

    as(ast[0], Literal.class, literal -> {
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

    as(ast[0], Negate.class, negate -> {
      as(negate.expression(), Identifier.class, id -> {
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
    as(ast[0], Literal.class, literal -> {

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
    as(ast[0], Call.class, call -> {
      as(call.target(), Paren.class, paren -> {
        as(paren.expression(), Callable.class, callable -> {
          as(callable.lhs(), Paren.class, call_lhs_paren -> {
            as(call_lhs_paren.expression(), Expressions.class, call_lhs_exprs -> {
              as(call_lhs_exprs.children()[0], Labeling.class, labeling -> {
                as(labeling.lhs(), Identifier.class, id -> {
                  Assertions.assertEquals("a", id.name());
                });
                as(labeling.rhs(), Identifier.class, id -> {
                  Assertions.assertEquals("int", id.name());
                });
              });
              as(call_lhs_exprs.children()[1], Labeling.class, labeling -> {
                as(labeling.lhs(), Identifier.class, id -> {
                  Assertions.assertEquals("b", id.name());
                });
                as(labeling.rhs(), Identifier.class, id -> {
                  Assertions.assertEquals("int", id.name());
                });
              });
            });
          });

          as(callable.rhs(), BinaryOperation.class, bop -> {
            Assertions.assertEquals(BinaryOperationKind.ADD, bop.kind());
          });
        });
      });

      as(call.paren(), Paren.class, paren -> {
        as(paren.expression(), Expressions.class, exprs -> {
          as(exprs.children()[0], Literal.class, literal -> Assertions.assertEquals("5", literal.content()));
          as(exprs.children()[1], Literal.class, literal -> Assertions.assertEquals("5", literal.content()));
        });
      });
    });


  }

  private Expression[] toExpressions(Expression expr) {

    if (expr instanceof Expressions exprs) {
      return exprs.children();
    } else if (expr instanceof Program program) {
      return toExpressions(program.children());
    } else {
      return new Expression[]{expr};
    }
  }

  private <T> void as(Expression exp, Class<T> clazz, Consumer<T> then) {

    Assertions.assertInstanceOf(clazz, exp);

    if (then != null) {
      then.accept((T) exp);
    }
  }

  private <T> T assertType(Class<T> clazz, Expression exp) {

    Assertions.assertInstanceOf(clazz, exp);
    return (T) exp;
  }

  private void isIdentifier(Expression exp, String expected) {

    Assertions.assertInstanceOf(Identifier.class, exp);
    Assertions.assertEquals(expected, ((Identifier) exp).name());
  }

  private void isLiteral(Expression exp, String expected) {

    Assertions.assertInstanceOf(Literal.class, exp);
    Assertions.assertEquals(expected, ((Literal) exp).content());
  }

  private <T, R> void is(Expression exp, Class<T> clazz, Function<T, R> mapper, Object expected) {

    Assertions.assertInstanceOf(clazz, exp);

    final var res = mapper.apply((T) exp);
    Assertions.assertEquals(expected, res);
  }

  @SneakyThrows
  private Program parseProgram(String code) {

    try (final var tokens = new PlangLexer(PlangTestUtil.stringToStream(code))) {
      final var parser = new TokenToAstRaising(tokens);

      return parser.parse();
    }
  }

  private static class ToStringTreeAstVisitor implements AstVisitor<String> {

    @Override
    public String visit(Expression expr) {

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
    public String visitLiteral(final Literal expr) {
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
