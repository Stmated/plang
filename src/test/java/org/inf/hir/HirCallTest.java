package org.inf.hir;

import org.inf.Inf;
import org.inf.ty.Ty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

class HirCallTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "f(...args)",
    "f ...args",
    "service.call(...args)",
    "service.call ...args",
    "f(...source.args)",
    "f(...factory())",
    "f(...values[0])",
    "f(...(1, 2))"
  })
  void given__spread_call__when__raised__then__operand_boundary_and_traversal_are_preserved(final String code) {
    final var expression = call(code);
    final var spread = Assertions.assertInstanceOf(Hir.Spread.class, expression.arguments()[0].value());
    final var operands = new ArrayList<Hir.Expression>();
    expression.visit(new HirVisitor() {
      @Override
      public void visitSpread(final Hir.Spread value) {
        operands.add(value.value());
        HirVisitor.super.visitSpread(value);
      }
    });
    expression.transform(new HirTransformer() {});
    Assertions.assertAll(
      () -> Assertions.assertNull(expression.arguments()[0].label()),
      () -> Assertions.assertEquals(List.of(spread.value()), operands),
      () -> Assertions.assertSame(spread, expression.arguments()[0].value())
    );
  }

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
    "f 1 | 1",
    "f a = 1 | 1",
    "f(a = 1) | 1",
    "f(1, 2) | 2",
    "f a = 1, b = 2 | 2",
    "f(a = 1, b = 2) | 2",
    "service.call() | 0",
    "service.call(a = 1) | 1",
    "service.call a = 1, b = 2 | 2",
    "~(f()) | 0",
    "~(f a = 1) | 1"
  })
  void given__call_forms__when__raised__then__arguments_are_always_an_array(final String code, final int count) {
    final var arguments = call(code).arguments();
    Assertions.assertNotNull(arguments);
    Assertions.assertEquals(count, arguments.length);
    for (final var argument : arguments) {
      Assertions.assertNotNull(argument);
    }
  }

  @Test
  void given__null_argument_array__when__constructed_or_assigned__then__rejected() {
    final var target = new Hir.Lexeme("f");
    final var expression = new Hir.Call(target, new Hir.Argument[0]);
    Assertions.assertAll(
      () -> Assertions.assertThrows(NullPointerException.class, () -> new Hir.Call(target, null)),
      () -> Assertions.assertThrows(NullPointerException.class, () -> new Hir.Call(target, null, false, null, null)),
      () -> Assertions.assertThrows(NullPointerException.class, () -> expression.arguments(null))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "f((1, true))",
    "f t = (1, true)",
    "f(t = (1, true))",
    "f((a = 1,))"
  })
  void given__tuple_argument__when__raised__then__one_entry_preserves_the_value_boundary(final String code) {
    final var entry = call(code).arguments()[0];
    Assertions.assertInstanceOf(Hir.Tuple.class, entry.value());
  }

  @Test
  void given__grouped_assignment_argument__when__raised__then__assignment_is_not_a_label() {
    final var entry = call("f((a = 1))").arguments()[0];
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
    final var entry = call(code).arguments()[0];
    Assertions.assertAll(
      () -> Assertions.assertEquals("a", entry.label().name()),
      () -> Assertions.assertInstanceOf(Hir.Literal.class, entry.value()),
      () -> Assertions.assertEquals("f(a=1)", call(code).toString())
    );
  }

  @Test
  void given__call_containers__when__visited__then__only_nested_tuple_values_use_tuple_hooks() {
    final var arguments = new ArrayList<Hir.Argument>();
    final var tuples = new ArrayList<Hir.Tuple>();
    final var tupleEntries = new ArrayList<Hir.TupleEntry>();
    final var literals = new ArrayList<Hir.Literal>();
    final HirVisitor visitor = new HirVisitor() {
      @Override
      public void visitCallArgument(final Hir.Argument expression) {
        arguments.add(expression);
        HirVisitor.super.visitCallArgument(expression);
      }

      @Override
      public void visitTuple(final Hir.Tuple expression) {
        tuples.add(expression);
        HirVisitor.super.visitTuple(expression);
      }

      @Override
      public void visitLiteral(final Hir.Literal expression) {
        literals.add(expression);
      }

      @Override
      public void visitTupleEntry(final Hir.TupleEntry expression) {
        tupleEntries.add(expression);
        HirVisitor.super.visitTupleEntry(expression);
      }
    };
    Inf.codeToHir("f()").visit(visitor);
    Inf.codeToHir("f(1)").visit(visitor);
    Inf.codeToHir("f(a = (2, true), b = 3)").visit(visitor);
    Assertions.assertAll(
      () -> Assertions.assertEquals(3, arguments.size()),
      () -> Assertions.assertEquals(1, tuples.size()),
      () -> Assertions.assertEquals(2, tupleEntries.size()),
      () -> Assertions.assertEquals(List.of("1", "2", "true", "3"), literals.stream().map(Hir.Literal::content).toList())
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
    final var entries = expression.arguments();
    Assertions.assertSame(container, expression.arguments());
    if (entries.length != 0) {
      Assertions.assertEquals("a", entries[0].label().name());
      final var value = entries[0].value();
      final var scalar = value instanceof Hir.Tuple tuple ? tuple.children()[0].value() : value;
      Assertions.assertEquals("9", Assertions.assertInstanceOf(Hir.Literal.class, scalar).content());
    }
  }

  @Test
  void given__argument_hook__when__transformed__then__replacement_preserves_the_label() {
    final var expression = call("f(a = 1)");
    final var original = expression.arguments()[0];
    expression.transform(new HirTransformer() {
      @Override
      public Hir.Argument transformCallArgument(final Hir.Argument argument) {
        return new Hir.Argument(argument.label(), new Hir.Literal("9", Ty.INTEGER));
      }
    });
    Assertions.assertAll(
      () -> Assertions.assertNotSame(original, expression.arguments()[0]),
      () -> Assertions.assertSame(original.label(), expression.arguments()[0].label()),
      () -> Assertions.assertEquals("f(a=9)", expression.toString())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val args = (1, true); f(args)",
    "val S = struct { val a: int; }; val args = new heap S { a = 1; }; f(args)"
  })
  void given__aggregate_reference__when__raised_and_transformed__then__one_argument_keeps_the_reference(final String code) {
    final var expression = call(code);
    final var reference = Assertions.assertInstanceOf(Hir.Identifier.class, expression.arguments()[0].value());
    expression.transform(new HirTransformer() {});
    Assertions.assertAll(
      () -> Assertions.assertEquals(1, expression.arguments().length),
      () -> Assertions.assertEquals("args", reference.lexeme().name()),
      () -> Assertions.assertSame(reference, expression.arguments()[0].value())
    );
  }

  @Test
  void given__non_returning_argument_value__when__types_are_queried__then__nominal_value_type_is_preserved() {
    final var value = new Hir.Block(new Hir.Literal("1", Ty.INTEGER), Ty.DEADEND);
    final var argument = new Hir.Argument(null, value);
    Assertions.assertAll(
      () -> Assertions.assertEquals(Ty.DEADEND, argument.ty()),
      () -> Assertions.assertEquals(Ty.INTEGER, argument.valueTy())
    );
  }
}
