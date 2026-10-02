package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HirTupleValidationVisitorPassTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "val t: (int, bool) = (1, 2)",
    "val t: (int,) = (1, true)",
    "val t: (int, bool) = ((1,), true)",
    "val t: (uint8, uint8) = (1, 2)",
    "val t: (int64, bool) = (1, true)",
    "val t: int = (1,)",
    "val t: (int,) = 1",
    "var t = (1, true); t = (false, 2)",
    "val f = (t: (int, bool)) => 1; f((1, 2))",
    "val f = (t: (int, bool)) => 1; f((1,))",
    "val f = (t: int) => 1; f((1,))",
    "val f = (): (int, bool) => (1, 2)",
    "val f = (): (int, bool) => { return (1, 2); }",
    "val f = (): int => (1,)",
    "val f = (): (int,) => 1",
    "val f = (t: (int, bool)) => 1; f(t: (1, 2))",
    "val f = (): (int, bool) => { return (1, true); return (1, 2); }",
    "val S = struct { val t: (int, bool); }; new heap S { t = (1, 2); }"
  })
  void given__incompatible_tuple_boundary__when__typed__then__explicit_conversion_error(String code) {
    final var error = Assertions.assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToThir(code));
    Assertions.assertEquals(true, error.getMessage().contains("Incompatible tuple"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f = (t: (int, bool)) => 1; f(1, true)",
    "val f = (t: (int, bool)) => 1; f()",
    "val f = (t: (int, bool)) => 1; f((1, true), (2, false))",
    "val f = (t: (int, bool)) => 1; f(other: (1, true))",
    "val f = (t: (int, bool)) => 1; f(t: (1, true), t: (2, false))"
  })
  void given__incorrect_tuple_call_arguments__when__typed__then__rejected(String code) {
    Assertions.assertThrows(RuntimeException.class, () -> Inf.codeToThir(code));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "(1, ())",
    "(1, { var x = 2; })",
    "val log = () => (); (1, log())",
    "val f = () => (1, { return true; }, ()); f()",
    "val f = () => (1, { return true; }, missing); f()",
    "val t: (1, 2) = (1, 2)",
    "val x = 1; val t: (x, bool) = (1, true)",
    "val t: (int, ()) = (1, 2)",
    "[(1, true); (1, true); 1]",
    "(label: 1)",
    "(label: 1,)",
    "(label: 1, 2)",
    "val t: (label: int, bool) = (1, true)"
  })
  void given__invalid_tuple_elements_or_annotations__when__typed__then__rejected(String code) {
    Assertions.assertThrows(IllegalArgumentException.class, () -> Inf.codeToThir(code));
  }
}
