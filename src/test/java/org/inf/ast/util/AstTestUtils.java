package org.inf.ast.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.inf.ast.Ast;
import org.inf.ast.TokenToAstRaising;
import org.inf.core.TriConsumer;
import org.inf.lexer.InfLexer;
import org.inf.parser.InfTestUtil;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@UtilityClass
public class AstTestUtils {

  public static Ast.Expression[] toExpressions(final Ast.Expression[] exprs) {
    return exprs;
  }

  public static Ast.Expression[] toExpressions(final Ast.Expression expr) {

    if (expr instanceof final Ast.HasChildren parent) {
      return parent.children();
    } else if (expr instanceof final Ast.Program program) {
      return toExpressions(program.children());
    } else {
      return new Ast.Expression[]{expr};
    }
  }

  public static <TA, TB, TC> void path(
    final Ast.Expression a,
    final Class<TA> ac,
    final Function<TA, Ast.Expression> bm, final Class<TB> bc,
    final Function<TB, Ast.Expression> cm, final Class<TC> cc,
    final TriConsumer<TA, TB, TC> then
  ) {

    assertInstanceOf(ac, a);
    final var b = bm.apply((TA) a);
    assertInstanceOf(bc, b);
    final var c = cm.apply((TB) b);
    assertInstanceOf(cc, c);

    then.accept((TA) a, (TB) b, (TC) c);
  }

  public static <T> void as(final Ast.Expression[] exprs, final Class<T> clazz, final Consumer<T> then) {

    assertEquals(1, exprs.length, "Expecting only 1 expression");

    as(exprs[0], clazz, then);
  }

  public static <T> void as(final Ast.Expression exp, final Class<T> clazz, final Consumer<T> then) {

    assertInstanceOf(clazz, exp);

    assertAll(
      "Assertions for expression: " + getExpressionDescription(exp),
      () -> then.accept((T) exp)
    );
  }

  @SafeVarargs
  public static <T> void asAll(final Ast.Expression exprs, final Class<T> c, final Consumer<T>... consumers) {
    asAll(toExpressions(exprs), c, consumers);
  }

  @SafeVarargs
  public static <T> void asAll(final Ast.Expression[] exprs, final Class<T> c, final Consumer<T>... consumers) {

    assertEquals(consumers.length, exprs.length, "There must be the same amount of consumers as array items");

    final var assertions = new Executable[exprs.length];
    for (var i = 0; i < exprs.length; i++) {

      final var expr = exprs[i];
      final var consumer = consumers[i];
      assertions[i] = () -> {
        assertInstanceOf(c, expr);
        assertAll(
          "Failed when asserting %s".formatted(getExpressionDescription(expr)),
          () -> consumer.accept((T) expr)
        );
      };
    }

    assertAll(
      "Assertions for expressions: %s".formatted(getExpressionsDescription(exprs)),
      assertions
    );
  }

  public static <TA extends Ast.Expression, TB extends Ast.Expression> void as(
    final Ast.Expression[] exprs,
    final Class<TA> ac, final Class<TB> bc,
    final BiConsumer<TA, TB> then
  ) {

    assertEquals(2, exprs.length, "Wrong number of expressions: %s".formatted(getExpressionsDescription(exprs)));

    assertAll(
      () -> assertInstanceOf(ac, exprs[0]),
      () -> assertInstanceOf(bc, exprs[1])
    );

    assertAll(
      "Assertions for expressions: %s".formatted(getExpressionsDescription(exprs)),
      () -> then.accept((TA) exprs[0], (TB) exprs[1])
    );
  }

  public static <TA, TB> void as(
    final Object a, final Class<TA> ac, final Consumer<TA> aa,
    final Object b, final Class<TB> bc, final Consumer<TB> ba
  ) {

    assertAll(
      () -> assertNotNull(a, "First expr must not be null"),
      () -> assertNotNull(b, "Second expr must not be null"),

      () -> assertInstanceOf(ac, a),
      () -> assertInstanceOf(bc, b)
    );

    assertAll(
      "Failed one of dual assertions: %s".formatted(getExpressionsDescription(a, b)),
      () -> assertAll("Failed first dual assertion: %s".formatted(getExpressionsDescription(a)), () -> aa.accept((TA) a)),
      () -> assertAll("Failed second dual assertion: %s".formatted(getExpressionsDescription(b)), () -> ba.accept((TB) b))
    );
  }

  public static <TA extends Ast.Expression, TB extends Ast.Expression, TC extends Ast.Expression> void as(
    final Ast.Expression a, final Class<TA> ac, final Consumer<TA> aa,
    final Ast.Expression b, final Class<TB> bc, final Consumer<TB> ba,
    final Ast.Expression c, final Class<TC> cc, final Consumer<TC> ca
  ) {

    assertAll(
      () -> assertNotNull(a, "First expr must not be null"),
      () -> assertNotNull(b, "Second expr must not be null"),
      () -> assertNotNull(c, "Third expr must not be null"),

      () -> assertInstanceOf(ac, a),
      () -> assertInstanceOf(bc, b),
      () -> assertInstanceOf(cc, c)
    );

    assertAll(
      () -> assertAll("Assertion for first tri expression: %s".formatted(getExpressionsDescription(a)), () -> aa.accept((TA) a)),
      () -> assertAll("Assertion for second tri expression: %s".formatted(getExpressionsDescription(b)), () -> ba.accept((TB) b)),
      () -> assertAll("Assertion for third tri expression: %s".formatted(getExpressionsDescription(c)), () -> ca.accept((TC) c))
    );
  }

  public static <TA extends Ast.Expression, TB extends Ast.Expression> void as(
    final Ast.Expression a, final Class<TA> ac,
    final Ast.Expression b, final Class<TB> bc,
    final BiConsumer<TA, TB> then
  ) {

    assertAll(
      () -> assertNotNull(a, "First expr must not be null"),
      () -> assertNotNull(b, "Second expr must not be null"),

      () -> assertInstanceOf(ac, a),
      () -> assertInstanceOf(bc, b)
    );

    assertAll(
      "Assertions for dual expressions: %s".formatted(getExpressionsDescription(a, b)),
      () -> then.accept((TA) a, (TB) b)
    );
  }

  public static String getExpressionsDescription(final Object... exprs) {
    return Arrays.stream(exprs).map(AstTestUtils::getExpressionDescription).collect(Collectors.joining(", "));
  }

  public static <T> String getExpressionDescription(final T expr) {

    // TODO: Refine this a whole lot, so we get actually useful simple and helpful strings.
    return expr.getClass().getSimpleName();
  }

  public static <TA extends Ast.Expression, TB extends Ast.Expression, TC extends Ast.Expression> void as(
    final Ast.Expression[] exprs,
    final Class<TA> ac, final Consumer<TA> aa,
    final Class<TB> bc, final Consumer<TB> ba
  ) {

    assertEquals(2, exprs.length);

    as(
      exprs[0], ac, aa,
      exprs[1], bc, ba
    );
  }

  public static <TA extends Ast.Expression, TB extends Ast.Expression, TC extends Ast.Expression> void as(
    final Ast.Expression[] exprs,
    final Class<TA> ac, final Consumer<TA> aa,
    final Class<TB> bc, final Consumer<TB> ba,
    final Class<TC> cc, final Consumer<TC> ca
  ) {

    assertEquals(3, exprs.length);

    as(
      exprs[0], ac, aa,
      exprs[1], bc, ba,
      exprs[2], cc, ca
    );
  }

  public static <T extends Ast.Expression> void as(
    final Ast.Expression a, final Ast.Expression b, final Ast.Expression c,
    final Class<T> clazz,
    final Consumer<T> aa,
    final Consumer<T> ba,
    final Consumer<T> ca
  ) {

    as(
      a, clazz, aa,
      b, clazz, ba,
      c, clazz, ca
    );
  }

  public static <T> T assertType(final Class<T> clazz, final Ast.Expression exp) {

    assertInstanceOf(clazz, exp);
    return (T) exp;
  }

  public static void isLexeme(final Ast.Expression exp, final String expected) {

    assertInstanceOf(Ast.Lexeme.class, exp);
    assertEquals(expected, ((Ast.Lexeme) exp).name());
  }

  public static Consumer<Ast.Lexeme> expectLexeme(final String expected) {
    return it -> assertEquals(expected, it.name());
  }

  public static void isLiteral(final Ast.Expression exp, final String expected) {

    assertInstanceOf(Ast.Literal.class, exp);
    assertEquals(expected, ((Ast.Literal) exp).content());
  }

  public static <T, R> void is(final Ast.Expression exp, final Class<T> clazz, final Function<T, R> mapper, final Object expected) {

    assertInstanceOf(clazz, exp);

    final var res = mapper.apply((T) exp);
    assertEquals(expected, res);
  }

  @SneakyThrows
  public static Ast.Program parseProgram(final String code) {

    try (final var tokens = new InfLexer(InfTestUtil.stringToStream(code))) {
      final var parser = new TokenToAstRaising(tokens);

      return parser.parse();
    }
  }
}
