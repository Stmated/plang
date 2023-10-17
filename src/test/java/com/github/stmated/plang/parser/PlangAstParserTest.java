package com.github.stmated.plang.parser;

import com.github.stmated.plang.ast.AstBinaryOperation;
import com.github.stmated.plang.ast.AstBinaryOperationType;
import com.github.stmated.plang.ast.AstExpression;
import com.github.stmated.plang.ast.AstIdentifier;
import com.github.stmated.plang.ast.AstLiteral;
import com.github.stmated.plang.ast.AstProgram;
import com.github.stmated.plang.ast.visitor.AstVisitor;
import com.github.stmated.plang.lexer.PlangLexer;
import com.github.stmated.plang.lexer.PlangLexerSteps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PlangAstParserTest {

  @Test
  void testParse() {

    final var program = this.parseProgram("1 + 1");

    Assertions.assertNotNull(program);
    Assertions.assertEquals(1, program.children().length);

    final var ibo = assertType(AstBinaryOperation.class, program.children()[0]);

    final var lhs = assertType(AstLiteral.class, ibo.lhs());
    final var rhs = assertType(AstLiteral.class, ibo.rhs());

    Assertions.assertEquals(1, lhs.value());
    Assertions.assertEquals(AstBinaryOperationType.ADD, ibo.type());
    Assertions.assertEquals(1, rhs.value());
  }

  @Test
  @SneakyThrows
  void testOperatorPrecedence2() {

    final var program = this.parseProgram("a < 1 && b > 2");

    Assertions.assertNotNull(program);
    Assertions.assertEquals(1, program.children().length);

    final var ibo = assertType(AstBinaryOperation.class, program.children()[0]);
    final var lhs = assertType(AstBinaryOperation.class, ibo.lhs());
    final var rhs = assertType(AstBinaryOperation.class, ibo.rhs());

    Assertions.assertEquals(AstBinaryOperationType.AND, ibo.type());
    Assertions.assertEquals(AstBinaryOperationType.LT, lhs.type());
    Assertions.assertEquals(AstBinaryOperationType.GT, rhs.type());
  }

  // TODO: Create tests that checks exact result of:
  //        * Result<(String, String), Error>
  //        * something<unit8>(2)

  @Test
  @SneakyThrows
  void testForLoop() {

    final var program = this.parseProgram("for (var i = 0; i < 10; i.increment()) { }");

//    as(program.children()[0], AstLoopFor.class, loop -> {
//      is(loop.assignments()[0].lhs(), AstVariableDeclaration.class, it -> it.identifier().name(), "i");
//
//      as(loop.predicate(), AstBinaryOperation.class, pred -> {
//        isIdentifier(pred.lhs(), "i");
//        Assertions.assertEquals(AstBinaryOperationType.LT, pred.type());
//        isLiteral(pred.rhs(), 10);
//      });
//
//      as(loop.steppers()[0], AstDotAccess.class, stepper_0 -> {
//        isIdentifier(stepper_0.lhs(), "i");
//        as(stepper_0.rhs(), AstCall.class, call -> {
//          isIdentifier(call.target(), "increment");
//        });
//      });
//    });
  }

  @Test
  @SneakyThrows
  void testOperatorPrecedence3() {

    final var program = this.parseProgram("a < 1 && b > 2 || x == 3");

    Assertions.assertNotNull(program);
    Assertions.assertEquals(1, program.children().length);

    as(program.children()[0], AstBinaryOperation.class, ibo -> {

      as(ibo.lhs(), AstBinaryOperation.class, lhs -> {
        Assertions.assertEquals(AstBinaryOperationType.LT, lhs.type());
      });

      as(ibo.rhs(), AstBinaryOperation.class, rhs -> {
        Assertions.assertEquals(AstBinaryOperationType.OR, rhs.type());

        as(rhs.lhs(), AstBinaryOperation.class, rhs_lhs -> {
          Assertions.assertEquals(AstBinaryOperationType.GT, rhs_lhs.type());
        });

        as(rhs.rhs(), AstBinaryOperation.class, rhs_rhs -> {
          Assertions.assertEquals(AstBinaryOperationType.Equals, rhs_rhs.type());
        });
      });
    });
  }

  @Test
  @SneakyThrows
  void testImportExport() {

    final var program = this.parseProgram("a < 1 && b > 2 || x == 3");

    Assertions.assertNotNull(program);
    Assertions.assertEquals(1, program.children().length);

    as(program.children()[0], AstBinaryOperation.class, ibo -> {

      as(ibo.lhs(), AstBinaryOperation.class, lhs -> {
        Assertions.assertEquals(AstBinaryOperationType.LT, lhs.type());
      });

      as(ibo.rhs(), AstBinaryOperation.class, rhs -> {
        Assertions.assertEquals(AstBinaryOperationType.OR, rhs.type());

        as(rhs.lhs(), AstBinaryOperation.class, rhs_lhs -> {
          Assertions.assertEquals(AstBinaryOperationType.GT, rhs_lhs.type());
        });

        as(rhs.rhs(), AstBinaryOperation.class, rhs_rhs -> {
          Assertions.assertEquals(AstBinaryOperationType.Equals, rhs_rhs.type());
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
      final var parser = new PlangAstParser(transformed);
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
      final var parser = new PlangAstParser(transformed);
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
      final var parser = new PlangAstParser(transformed);
      final var program = parser.parse();
      Assertions.assertNotNull(program);

      final var treePrintVisitor = new ToStringTreeAstVisitor();
      final var treeString = treePrintVisitor.visit(program);
      System.out.println(treeString);
    }
  }

  @Test
  void testBenchmark() throws IOException {

    final var ITERATIONS = 1_000;
    final var before = System.nanoTime();
    for (var i = 0; i < ITERATIONS; i++) {
      for (final var file : PlangTestUtil.getTestFilePaths()) {
        this.testAllFiles(file);
      }
    }

    final var after = System.nanoTime();
    final var duration = Duration.ofNanos(after - before);
    final var durationPer = duration.dividedBy(ITERATIONS);

    System.out.printf("Duration: %s, per %sns %sms", duration, durationPer.toNanos(), durationPer.toMillis());
    System.out.println();
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
    Assertions.assertEquals(expected, ((AstIdentifier)exp).name());
  }

  private void isLiteral(AstExpression exp, Object expected) {

    Assertions.assertInstanceOf(AstLiteral.class, exp);
    Assertions.assertEquals(expected, ((AstLiteral)exp).value());
  }

  private <T, R> void is(AstExpression exp, Class<T> clazz, Function<T, R> mapper, Object expected) {

    Assertions.assertInstanceOf(clazz, exp);

    final var res = mapper.apply((T) exp);
    Assertions.assertEquals(expected, res);
  }

  @SneakyThrows
  private AstProgram parseProgram(String code) {

    try (final var tokens = new PlangLexer(PlangTestUtil.stringToStream(code))) {
      final var parser = new PlangAstParser(tokens);

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
      return "  " + expr.value() + "\n";
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
