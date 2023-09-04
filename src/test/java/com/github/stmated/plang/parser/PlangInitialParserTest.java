package com.github.stmated.plang.parser;

import com.github.stmated.plang.ipr.InitialBinaryOperation;
import com.github.stmated.plang.ipr.InitialBinaryOperationType;
import com.github.stmated.plang.ipr.InitialExpression;
import com.github.stmated.plang.ipr.InitialLiteral;
import com.github.stmated.plang.ipr.InitialProgram;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PlangInitialParserTest {

  @Test
  void testParse() {

    final var program = this.parseProgram("1 + 1");

    Assertions.assertNotNull(program);
    Assertions.assertEquals(1, program.children().length);

    final var ibo = assertType(InitialBinaryOperation.class, program.children()[0]);

    final var lhs = assertType(InitialLiteral.class, ibo.lhs());
    final var rhs = assertType(InitialLiteral.class, ibo.rhs());

    Assertions.assertEquals(1, lhs.value());
    Assertions.assertEquals(InitialBinaryOperationType.ADD, ibo.type());
    Assertions.assertEquals(1, rhs.value());
  }

  @Test
  @SneakyThrows
  void testOperatorPrecedence2() {

    final var program = this.parseProgram("a < 1 && b > 2");

    Assertions.assertNotNull(program);
    Assertions.assertEquals(1, program.children().length);

    final var ibo = assertType(InitialBinaryOperation.class, program.children()[0]);
    final var lhs = assertType(InitialBinaryOperation.class, ibo.lhs());
    final var rhs = assertType(InitialBinaryOperation.class, ibo.rhs());

    Assertions.assertEquals(InitialBinaryOperationType.AND, ibo.type());
    Assertions.assertEquals(InitialBinaryOperationType.LT, lhs.type());
    Assertions.assertEquals(InitialBinaryOperationType.GT, rhs.type());
  }

  @Test
  @SneakyThrows
  void testOperatorPrecedence3() {

    final var program = this.parseProgram("a < 1 && b > 2 || x == 3");

    Assertions.assertNotNull(program);
    Assertions.assertEquals(1, program.children().length);

    assertStructure(program.children()[0], InitialBinaryOperation.class, ibo -> {

      assertStructure(ibo.lhs(), InitialBinaryOperation.class, lhs -> {
        Assertions.assertEquals(InitialBinaryOperationType.LT, lhs.type());
      });

      assertStructure(ibo.rhs(), InitialBinaryOperation.class, rhs -> {
        Assertions.assertEquals(InitialBinaryOperationType.OR, rhs.type());

        assertStructure(rhs.lhs(), InitialBinaryOperation.class, rhs_lhs -> {
          Assertions.assertEquals(InitialBinaryOperationType.GT, rhs_lhs.type());
        });

        assertStructure(rhs.rhs(), InitialBinaryOperation.class, rhs_rhs -> {
          Assertions.assertEquals(InitialBinaryOperationType.Equals, rhs_rhs.type());
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

    assertStructure(program.children()[0], InitialBinaryOperation.class, ibo -> {

      assertStructure(ibo.lhs(), InitialBinaryOperation.class, lhs -> {
        Assertions.assertEquals(InitialBinaryOperationType.LT, lhs.type());
      });

      assertStructure(ibo.rhs(), InitialBinaryOperation.class, rhs -> {
        Assertions.assertEquals(InitialBinaryOperationType.OR, rhs.type());

        assertStructure(rhs.lhs(), InitialBinaryOperation.class, rhs_lhs -> {
          Assertions.assertEquals(InitialBinaryOperationType.GT, rhs_lhs.type());
        });

        assertStructure(rhs.rhs(), InitialBinaryOperation.class, rhs_rhs -> {
          Assertions.assertEquals(InitialBinaryOperationType.Equals, rhs_rhs.type());
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

    final var pass2 = new PlangLexer2ndPass();
    try (final var tokens = new PlangLexer(Files.newInputStream(path))) {
      final var transformed = pass2.transform(tokens);
      final var parser = new PlangInitialParser(transformed);
      final var program = parser.parse();
      Assertions.assertNotNull(program);
    }
  }

  @Test
  void testBenchmark() {

    final var ITERATIONS = 1_000_000;
    final var before = System.nanoTime();
    for (var i = 0; i < ITERATIONS; i++) {
      this.testParse();
    }

    final var after = System.nanoTime();
    final var duration = Duration.ofNanos(after - before);
    final var durationPer = duration.dividedBy(ITERATIONS);

    System.out.printf("Duration: %s, per %sns %sms", duration, durationPer.toNanos(), durationPer.toMillis());
    System.out.println();
  }

  private <T> void assertStructure(InitialExpression exp, Class<T> clazz, Consumer<T> then) {

    Assertions.assertInstanceOf(clazz, exp);

    if (then != null) {
      then.accept((T) exp);
    }
  }

  private <T> T assertType(Class<T> clazz, InitialExpression exp) {

    Assertions.assertInstanceOf(clazz, exp);
    return (T) exp;
  }

  @SneakyThrows
  private InitialProgram parseProgram(String code) {

    try (final var tokens = new PlangLexer(PlangTestUtil.stringToStream(code))) {
      final var parser = new PlangInitialParser(tokens);

      return parser.parse();
    }
  }
}
