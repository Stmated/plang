package com.github.stmated.plang.parser;

import static com.github.stmated.plang.lexer.TokenType.ARROW_DOUBLE;
import static com.github.stmated.plang.lexer.TokenType.ASSIGN;
import static com.github.stmated.plang.lexer.TokenType.BIT_SHIFT_LEFT;
import static com.github.stmated.plang.lexer.TokenType.CLOSE_BRACE;
import static com.github.stmated.plang.lexer.TokenType.CLOSE_PAREN;
import static com.github.stmated.plang.lexer.TokenType.COLON;
import static com.github.stmated.plang.lexer.TokenType.COMMA;
import static com.github.stmated.plang.lexer.TokenType.DIVIDE;
import static com.github.stmated.plang.lexer.TokenType.DOUBLE_DOT;
import static com.github.stmated.plang.lexer.TokenType.ELSE;
import static com.github.stmated.plang.lexer.TokenType.EQUALS;
import static com.github.stmated.plang.lexer.TokenType.GTE;
import static com.github.stmated.plang.lexer.TokenType.IDENTIFIER;
import static com.github.stmated.plang.lexer.TokenType.IF;
import static com.github.stmated.plang.lexer.TokenType.LITERAL_DECIMAL;
import static com.github.stmated.plang.lexer.TokenType.LITERAL_DOUBLE;
import static com.github.stmated.plang.lexer.TokenType.LITERAL_INTEGER;
import static com.github.stmated.plang.lexer.TokenType.MODULUS;
import static com.github.stmated.plang.lexer.TokenType.MULTIPLY;
import static com.github.stmated.plang.lexer.TokenType.OPEN_BRACE;
import static com.github.stmated.plang.lexer.TokenType.OPEN_PAREN;
import static com.github.stmated.plang.lexer.TokenType.ADD;
import static com.github.stmated.plang.lexer.TokenType.REMAINDER;
import static com.github.stmated.plang.lexer.TokenType.RETURN;
import static com.github.stmated.plang.lexer.TokenType.SEMI_COLON;
import static com.github.stmated.plang.lexer.TokenType.SUBTRACT;
import static com.github.stmated.plang.lexer.TokenType.THEN;
import static com.github.stmated.plang.lexer.TokenType.VAL;

import com.github.stmated.plang.exceptions.InvalidDecimalsException;
import com.github.stmated.plang.exceptions.LexerException;
import com.github.stmated.plang.exceptions.UncaughtLexerException;
import com.github.stmated.plang.exceptions.UnexpectedTokenException;
import com.github.stmated.plang.lexer.PlangLexer;
import com.github.stmated.plang.lexer.PlangLexerSteps;
import com.github.stmated.plang.lexer.Token;
import com.github.stmated.plang.lexer.TokenType;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;

@Slf4j
class PlangLexerTest {

  @Test
  void testNumbers() {

    this.check("1", LITERAL_INTEGER);
    this.check("1.1", LITERAL_DOUBLE);
    this.check("0.1", LITERAL_DOUBLE);

    this.check("123", LITERAL_INTEGER);
    this.check("123.123", LITERAL_DOUBLE);
    this.check("0.123", LITERAL_DOUBLE);

    this.check("123_123", LITERAL_INTEGER);
    this.check("123_123.123_123", LITERAL_DOUBLE);

    this.check("123..123", LITERAL_INTEGER, DOUBLE_DOT, LITERAL_INTEGER);
  }

  @Test
  void testDifficultDecimalRange() {
    this.check("123.456..123.456", LITERAL_DOUBLE, DOUBLE_DOT, LITERAL_DOUBLE);
    this.checkToString("123.456..123.456", "123.456..123.456");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1.1m",
    "1.1M"
  })
  @SneakyThrows
  void testDecimalContent(String code) {

    final var tokens = this.execute(code);

    Assertions.assertEquals(1, tokens.size());
    Assertions.assertEquals(LITERAL_DECIMAL, tokens.getFirst().type());
    Assertions.assertEquals(0, tokens.getFirst().start());
    Assertions.assertEquals(4, tokens.getFirst().end());
    Assertions.assertEquals("1.1", tokens.getFirst().content());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1.1"
  })
  @SneakyThrows
  void testDoubleContent(String code) {

    final var tokens = this.execute(code);

    Assertions.assertEquals(1, tokens.size());
    Assertions.assertEquals(LITERAL_DOUBLE, tokens.getFirst().type());
    Assertions.assertEquals(0, tokens.getFirst().start());
    Assertions.assertEquals(3, tokens.getFirst().end());
    Assertions.assertEquals("1.1", tokens.getFirst().content());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1.1d",
    "1.1D"
  })
  @SneakyThrows
  void testDoubleContentWithSuffix(String code) {

    final var tokens = this.execute(code);

    Assertions.assertEquals(1, tokens.size());
    Assertions.assertEquals(LITERAL_DOUBLE, tokens.getFirst().type());
    Assertions.assertEquals(0, tokens.getFirst().start());
    Assertions.assertEquals(4, tokens.getFirst().end());
    Assertions.assertEquals("1.1", tokens.getFirst().content());
  }

  @Test
  void testIntegerSuffix1() {
    final var tokens = this.execute("1u8");
    Assertions.assertEquals(1, tokens.size());
    Assertions.assertEquals(LITERAL_INTEGER, tokens.getFirst().type());
    Assertions.assertEquals(0, tokens.getFirst().start());
    Assertions.assertEquals(3, tokens.getFirst().end());
  }

  @Test
  void testIntegerSuffix2() {
    final var tokens = this.execute("11u8");
    Assertions.assertEquals(1, tokens.size());
    Assertions.assertEquals(LITERAL_INTEGER, tokens.getFirst().type());
    Assertions.assertEquals(0, tokens.getFirst().start());
    Assertions.assertEquals(4, tokens.getFirst().end());
  }

  @Test
  void testIntegerSuffix3() {
    final var tokens = this.execute("123u128");
    Assertions.assertEquals(1, tokens.size());
    Assertions.assertEquals(LITERAL_INTEGER, tokens.getFirst().type());
    Assertions.assertEquals(0, tokens.getFirst().start());
    Assertions.assertEquals(7, tokens.getFirst().end());
  }

  @Test
  void testIntegerSuffix4() {
    final var tokens = this.execute("123i64");
    Assertions.assertEquals(1, tokens.size());
    Assertions.assertEquals(LITERAL_INTEGER, tokens.getFirst().type());
    Assertions.assertEquals(0, tokens.getFirst().start());
    Assertions.assertEquals(6, tokens.getFirst().end());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "1 + 1",
    "1+1",
    "  1+1",
    "  1+1  "
  })
  void testOnePlusOne(String code) {
    this.check(code, LITERAL_INTEGER, ADD, LITERAL_INTEGER);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "-1",
    " -1",
    " - 1 "
  })
  void testUnaryNegate(String code) {
    this.check(code, SUBTRACT, LITERAL_INTEGER);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "+1",
    " +1",
    " + 1 "
  })
  void testUnaryAdd(String code) {
    this.check(code, ADD, LITERAL_INTEGER);
  }

  @Test
  void testParen() {

    this.check("(1)", OPEN_PAREN, LITERAL_INTEGER, CLOSE_PAREN);
    this.check("(1 + 1)", OPEN_PAREN, LITERAL_INTEGER, ADD, LITERAL_INTEGER, CLOSE_PAREN);
  }

  @Test
  void testIdentifiersAndKeywords() {

    this.check("something", IDENTIFIER);
    this.check("something + other", IDENTIFIER, ADD, IDENTIFIER);
    this.check("if", IF);
    this.check("iff", IDENTIFIER);

    this.check(
        "if (1 >= 1) then return 10 << 1",
        IF, OPEN_PAREN, LITERAL_INTEGER, GTE, LITERAL_INTEGER, CLOSE_PAREN,
        THEN, RETURN, LITERAL_INTEGER, BIT_SHIFT_LEFT, LITERAL_INTEGER
    );
  }

  @Test
  void testParenIncluded() {
    this.check(
      "if (a % b == 0) bar",
      IF, OPEN_PAREN, IDENTIFIER, REMAINDER, IDENTIFIER, EQUALS, LITERAL_INTEGER, CLOSE_PAREN, IDENTIFIER
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

  @Test
  void testTokenizerErrors() {
    Assertions.assertThrows(InvalidDecimalsException.class, () ->  this.execute("123.123.123"));
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

  @SneakyThrows
  private void checkToString(String from, String expected) {

    final var tokenList = this.execute(from);
    final var actual = String.join("", tokenList.stream().map(Token::content).toList());

    Assertions.assertEquals(expected, actual);
  }

  @SneakyThrows
  private List<Token> execute(String from) {

    final var pass2 = new PlangLexerSteps();
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
      } catch (Throwable ex) {

        final var tokens = list.stream().map(Object::toString).toList();
        final var tokenStrings = String.join(", ", tokens);

        throw new UncaughtLexerException("Exception after %s".formatted(tokenStrings), ex);
      }
    }

    return list;
  }
}
