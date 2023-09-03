package com.github.stmated.plang.parser;

import static com.github.stmated.plang.parser.TokenType.ARROW_DOUBLE;
import static com.github.stmated.plang.parser.TokenType.ASSIGN;
import static com.github.stmated.plang.parser.TokenType.BIT_SHIFT_LEFT;
import static com.github.stmated.plang.parser.TokenType.CLOSE_BRACE;
import static com.github.stmated.plang.parser.TokenType.CLOSE_PAREN;
import static com.github.stmated.plang.parser.TokenType.COLON;
import static com.github.stmated.plang.parser.TokenType.COMMA;
import static com.github.stmated.plang.parser.TokenType.DIVIDE;
import static com.github.stmated.plang.parser.TokenType.DOUBLE_DOT;
import static com.github.stmated.plang.parser.TokenType.ELSE;
import static com.github.stmated.plang.parser.TokenType.EQUALS;
import static com.github.stmated.plang.parser.TokenType.GTE;
import static com.github.stmated.plang.parser.TokenType.IDENTIFIER;
import static com.github.stmated.plang.parser.TokenType.IF;
import static com.github.stmated.plang.parser.TokenType.LITERAL_DECIMAL;
import static com.github.stmated.plang.parser.TokenType.LITERAL_INTEGER;
import static com.github.stmated.plang.parser.TokenType.MULTIPLY;
import static com.github.stmated.plang.parser.TokenType.OPEN_BRACE;
import static com.github.stmated.plang.parser.TokenType.OPEN_PAREN;
import static com.github.stmated.plang.parser.TokenType.PLUS;
import static com.github.stmated.plang.parser.TokenType.REMAINDER;
import static com.github.stmated.plang.parser.TokenType.RETURN;
import static com.github.stmated.plang.parser.TokenType.SEMI_COLON;
import static com.github.stmated.plang.parser.TokenType.THEN;
import static com.github.stmated.plang.parser.TokenType.VAL;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PlangLexerTest {

  @Test
  void testNumbers() {

    this.check("1", LITERAL_INTEGER);
    this.check("1.1", LITERAL_DECIMAL);
    this.check(".1", LITERAL_DECIMAL);

    this.check("123", LITERAL_INTEGER);
    this.check("123.123", LITERAL_DECIMAL);
    this.check(".123", LITERAL_DECIMAL);

    this.check("123_123", LITERAL_INTEGER);
    this.check("123_123.123_123", LITERAL_DECIMAL);

    this.check("123..123", LITERAL_INTEGER, DOUBLE_DOT, LITERAL_INTEGER);
    this.check("123.456..123.456", LITERAL_DECIMAL, DOUBLE_DOT, LITERAL_DECIMAL);
  }

  @Test
  @SneakyThrows
  void testDecimalContent() {

    final var tokens = this.execute("1.1");

    Assertions.assertEquals(1, tokens.size());
    Assertions.assertEquals(LITERAL_DECIMAL, tokens.get(0).type());
    Assertions.assertEquals(0, tokens.get(0).start());
    Assertions.assertEquals(3, tokens.get(0).end());
    Assertions.assertEquals("1.1", tokens.get(0).content());
  }

  @Test
  void testOnePlusOne() {

    this.check("1 + 1", LITERAL_INTEGER, PLUS, LITERAL_INTEGER);
    this.check("1+1", LITERAL_INTEGER, PLUS, LITERAL_INTEGER);
    this.check("  1+1", LITERAL_INTEGER, PLUS, LITERAL_INTEGER);
    this.check("  1+1  ", LITERAL_INTEGER, PLUS, LITERAL_INTEGER);
  }

  @Test
  void testParen() {

    this.check("(1)", OPEN_PAREN, LITERAL_INTEGER, CLOSE_PAREN);
    this.check("(1 + 1)", OPEN_PAREN, LITERAL_INTEGER, PLUS, LITERAL_INTEGER, CLOSE_PAREN);
  }

  @Test
  void testIdentifiersAndKeywords() {

    this.check("something", IDENTIFIER);
    this.check("something + other", IDENTIFIER, PLUS, IDENTIFIER);
    this.check("if", IF);
    this.check("iff", IDENTIFIER);

    this.check(
        "if (1 >= 1) then return 10 << 1",
        IF, OPEN_PAREN, LITERAL_INTEGER, GTE, LITERAL_INTEGER, CLOSE_PAREN,
        THEN, RETURN, LITERAL_INTEGER, BIT_SHIFT_LEFT, LITERAL_INTEGER
    );
  }

  @Test
  void testCode() {

    final String code = "val fn = (a: uint32, b: uint32): uint32 => {" +
        "    if (a % 2 == 0) then return a * b;" +
        "    else return a / b;" +
        "}";

    this.check(
        code,
        VAL, IDENTIFIER, ASSIGN, OPEN_PAREN, IDENTIFIER, COLON, IDENTIFIER, COMMA, IDENTIFIER, COLON, IDENTIFIER, CLOSE_PAREN, COLON, IDENTIFIER, ARROW_DOUBLE, OPEN_BRACE,
        IF, OPEN_PAREN, IDENTIFIER, REMAINDER, LITERAL_INTEGER, EQUALS, LITERAL_INTEGER, CLOSE_PAREN, THEN, RETURN, IDENTIFIER, MULTIPLY, IDENTIFIER, SEMI_COLON,
        ELSE, RETURN, IDENTIFIER, DIVIDE, IDENTIFIER, SEMI_COLON,
        CLOSE_BRACE
    );
  }

  @Test
  @SneakyThrows
  void testIndexAndContent() {

    final var tokenList = this.execute("1 + 1");
    Assertions.assertEquals(3, tokenList.size());

    Assertions.assertEquals(0, tokenList.get(0).start());
    Assertions.assertEquals(1, tokenList.get(0).end());
    Assertions.assertEquals("1", tokenList.get(0).content());

    Assertions.assertEquals(2, tokenList.get(1).start());
    Assertions.assertEquals(3, tokenList.get(1).end());
    Assertions.assertEquals("+", tokenList.get(1).content());

    Assertions.assertEquals(4, tokenList.get(2).start());
    Assertions.assertEquals(5, tokenList.get(2).end());
    Assertions.assertEquals("1", tokenList.get(2).content());
  }

  public static Stream<Arguments> allValidTestFiles() throws IOException {
    return PlangTestUtil.testShouldSucceedSource();
  }

  @ParameterizedTest
  @MethodSource("allValidTestFiles")
  @SneakyThrows
  void testAllFiles(Path path) {

    // Does not test real validity, just that it does not crash.
    try (final var tokens = new PlangLexer(Files.newInputStream(path))) {
       this.iteratorToList(tokens);
    }
  }

  @Test
  void testBenchmark() {

    final var ITERATIONS = 1_000_000;
    final var before = System.nanoTime();
    for (var i = 0; i < ITERATIONS; i++) {
      this.testCode();
    }

    final var after = System.nanoTime();
    final var duration = Duration.ofNanos(after - before);
    final var durationPer = duration.dividedBy(ITERATIONS);

    System.out.printf("Duration: %s, per %sns %sms", duration, durationPer.toNanos(), durationPer.toMillis());
    System.out.println();
  }

  @Test
  void testTokenizerErrors() {

    Assertions.assertThrows(IllegalArgumentException.class, () -> this.execute("123.123.123"));
  }

  @SneakyThrows
  private void check(String from, TokenType... expected) {

    final var tokenList = this.execute(from);
    final var actual = tokenList.stream().map(Token::type).toList().toArray(new TokenType[0]);

    Assertions.assertArrayEquals(expected, actual, () -> {

      final var expectedStrings = Arrays.stream(expected).map(TokenType::toString).toList();
      final var actualStrings = Arrays.stream(actual).map(TokenType::toString).toList();

      return "\n%s\n%s\n".formatted(expectedStrings, actualStrings);
    });
  }

  private List<Token> execute(String from) throws Exception {

    final var pass2 = new PlangLexer2ndPass();
    try (final var tokens = new PlangLexer(PlangTestUtil.stringToStream(from))) {
      final var transformed = pass2.transform(tokens);
      return this.iteratorToList(transformed);
    }
  }

  private <T> List<T> iteratorToList(Iterator<T> iterator) {

    final var list = new ArrayList<T>();
    while (iterator.hasNext()) {
      try {
        list.add(iterator.next());
      } catch (Exception ex) {

        final var tokens = list.stream().map(Object::toString).toList();
        final var tokenStrings = String.join(", ", tokens);

        throw new RuntimeException("Exception after %s".formatted(tokenStrings), ex);
      }
    }

    return list;
  }
}
