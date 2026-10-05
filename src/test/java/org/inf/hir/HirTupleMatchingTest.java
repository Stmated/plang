package org.inf.hir;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.ty.TyStruct;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.ArrayList;

class HirTupleMatchingTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "(1, true, 2) | [0, 1, 2]",
    "(a = 1, true, b = 2) | [0, 1, 2]",
    "(b = 2, a = 1, true) | [2, 0, 1]",
    "(true, b = 2, a = 1) | [1, 2, 0]",
    "(true, a = 1, 2) | [1, 0, 2]",
    "(b = 2, 1, true) | [2, 0, 1]"
  })
  void given__mixed_source__when__matched__then__labels_reserve_slots_before_positions(
    final String source, final String expected
  ) {
    final var tuple = tuple(source);
    final var destination = Assertions.assertInstanceOf(TyStruct.class, Inf.codeToThir("(a = 0, false, b = 0)").root().ty());
    final var entries = tuple.children();
    Assertions.assertAll(
      () -> Assertions.assertEquals(expected, Arrays.toString(HirTupleMatching.match(tuple, destination))),
      () -> Assertions.assertSame(entries, tuple.children())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "(a = 1,)",
    "(1, true, b = 2, 3)"
  })
  void given__incompatible_arity__when__matched__then__flow_validation_decides_validity(final String source) {
    final var tuple = tuple(source);
    final var destination = Assertions.assertInstanceOf(TyStruct.class, Inf.codeToThir("(a = 0, false, b = 0)").root().ty());
    Assertions.assertNull(HirTupleMatching.match(tuple, destination));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "(missing = 1, true, b = 2)",
    "(a = 1, false, missing = 2)"
  })
  void given__unknown_label__when__matched__then__explicit_error(final String source) {
    final var tuple = tuple(source);
    final var destination = Assertions.assertInstanceOf(TyStruct.class, Inf.codeToThir("(a = 0, false, b = 0)").root().ty());
    final var error = Assertions.assertThrows(InvalidTypeConversionException.class, () -> HirTupleMatching.match(tuple, destination));
    Assertions.assertTrue(error.getMessage().contains("Unknown tuple label: missing"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "(a = 1, a = 2, true)",
    "(a = 1, true, b = 2, a = 3)"
  })
  void given__duplicate_label__when__matched__then__explicit_error(final String source) {
    final var tuple = tuple(source);
    final var destination = Assertions.assertInstanceOf(TyStruct.class, Inf.codeToThir("(a = 0, false, b = 0)").root().ty());
    final var error = Assertions.assertThrows(IllegalArgumentException.class, () -> HirTupleMatching.match(tuple, destination));
    Assertions.assertEquals("Duplicate tuple label: a", error.getMessage());
  }

  private static Hir.Tuple tuple(final String code) {
    final var tuples = new ArrayList<Hir.Tuple>();
    Inf.codeToHir(code).visit(new HirVisitor() {
      @Override
      public void visitTuple(final Hir.Tuple expression) {
        tuples.add(expression);
        HirVisitor.super.visitTuple(expression);
      }
    });
    Assertions.assertEquals(1, tuples.size());
    return tuples.getFirst();
  }
}
