package org.inf.hir;

import org.inf.Inf;
import org.inf.ty.Ty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;

class HirCallArgumentsTest {

  private static Hir.Call call(final String code) {
    final var calls = new ArrayList<Hir.Call>();
    Inf.codeToHir(code).visit(new HirVisitor() {
      @Override
      public void visitCall(final Hir.Call expression) {
        calls.add(expression);
      }
    });
    Assertions.assertEquals(1, calls.size());
    return calls.getFirst();
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "f() | 0",
    "f(1) | 1",
    "f(1,) | 1",
    "f a = 1 | 1",
    "f(a = 1) | 1",
    "f(1, 2) | 2",
    "f a = 1, b = 2 | 2",
    "f(a = 1, b = 2) | 2"
  })
  void given__call_forms__when__raised__then__container_is_compact(final String code, final int count) {
    final var expression = call(code);
    final var entries = HirCallArguments.entries(expression.arguments());
    Assertions.assertEquals(count, entries.size());
    switch (count) {
      case 0 -> Assertions.assertNull(expression.arguments());
      case 1 -> Assertions.assertSame(entries.getFirst(), expression.arguments());
      default -> Assertions.assertInstanceOf(Hir.Tuple.class, expression.arguments());
    }
    Assertions.assertTrue(entries.stream().noneMatch(Hir.TupleEntry::typeLabel));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "f((1, true))",
    "f t = (1, true)",
    "f(t = (1, true))",
    "f((a = 1,))"
  })
  void given__tuple_argument__when__raised__then__one_entry_preserves_the_value_boundary(final String code) {
    final var entry = Assertions.assertInstanceOf(Hir.TupleEntry.class, call(code).arguments());
    Assertions.assertInstanceOf(Hir.Tuple.class, entry.value());
  }

  @Test
  void given__grouped_assignment_argument__when__raised__then__assignment_is_not_a_label() {
    final var entry = Assertions.assertInstanceOf(Hir.TupleEntry.class, call("f((a = 1))").arguments());
    Assertions.assertAll(
      () -> Assertions.assertNull(entry.label()),
      () -> Assertions.assertInstanceOf(Hir.Assignment.class, entry.value())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "f(a: 1)",
    "f a: 1",
    "f(1, b: 2)",
    "f a = 1, b: 2"
  })
  void given__colon_named_argument__when__raised__then__syntax_is_rejected(final String code) {
    final var error = Assertions.assertThrows(IllegalArgumentException.class, () -> Inf.codeToHir(code));
    Assertions.assertTrue(error.getMessage().contains("Named call arguments require '='"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "f(a = 1)",
    "f a = 1"
  })
  void given__named_call_entry__when__raised__then__label_is_not_an_assignment(final String code) {
    final var entry = Assertions.assertInstanceOf(Hir.TupleEntry.class, call(code).arguments());
    Assertions.assertAll(
      () -> Assertions.assertEquals("a", entry.label().name()),
      () -> Assertions.assertInstanceOf(Hir.Literal.class, entry.value()),
      () -> Assertions.assertEquals("f(a=1)", call(code).toString())
    );
  }

  @Test
  void given__call_containers__when__visited__then__only_nested_tuple_values_use_tuple_hooks() {
    final var tuples = new ArrayList<Hir.Tuple>();
    final var literals = new ArrayList<Hir.Literal>();
    final HirVisitor visitor = new HirVisitor() {
      @Override
      public void visitTuple(final Hir.Tuple expression) {
        tuples.add(expression);
        HirVisitor.super.visitTuple(expression);
      }

      @Override
      public void visitLiteral(final Hir.Literal expression) {
        literals.add(expression);
      }
    };
    Inf.codeToHir("f()").visit(visitor);
    Inf.codeToHir("f(1)").visit(visitor);
    Inf.codeToHir("f(a = (2, true), b = 3)").visit(visitor);
    Assertions.assertAll(
      () -> Assertions.assertEquals(1, tuples.size()),
      () -> Assertions.assertEquals(4, literals.size())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "f()",
    "f(a = 1)",
    "f(a = (1, true), b = 2)"
  })
  void given__call_containers__when__transformed__then__labels_and_boundaries_are_preserved(final String code) {
    final var expression = call(code);
    final var container = expression.arguments();
    expression.transform(new HirTransformer() {
      @Override
      public Hir.Expression transformTuple(final Hir.Tuple tuple) {
        Assertions.assertEquals(2, tuple.children().length);
        Assertions.assertNull(tuple.children()[0].label());
        return HirTransformer.super.transformTuple(tuple);
      }

      @Override
      public Hir.Expression transformLiteral(final Hir.Literal literal) {
        return literal.ty() == Ty.INTEGER ? new Hir.Literal("9", Ty.INTEGER) : literal;
      }
    });
    final var entries = HirCallArguments.entries(expression.arguments());
    Assertions.assertSame(container, expression.arguments());
    if (!entries.isEmpty()) {
      Assertions.assertEquals("a", entries.getFirst().label().name());
      final var value = entries.getFirst().value();
      final var scalar = value instanceof Hir.Tuple tuple ? tuple.children()[0].value() : value;
      Assertions.assertEquals("9", Assertions.assertInstanceOf(Hir.Literal.class, scalar).content());
    }
  }
}
