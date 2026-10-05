package org.inf.hir.passes;

import org.inf.Inf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class HirTupleSyntaxValidationVisitorPassTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "(a = 1,)",
    "(a = 1, true, b = 2)",
    "val t: (a: int,) = (a = 1,)",
    "val t: (a: int, bool, b: int) = (a = 1, true, b = 2)",
    "val f = (t: (a: int,)): (a: int,) => t; f((a = 1,))",
    "val S = struct { val t: (a: int,); }; new heap S { t = (a = 1,); }",
    "[(a = 1,); (a: int,); 1]",
    "val f = (a: int) => a; f(a = 1)",
    "var a = 1; (a = 2); a",
    "(a = { var x = 1; x = 2; x },)",
    "val t = ({ val x: (a: int,) = (a = 1,); x },)"
  })
  void given__valid_label_syntax__when__checked__then__context_boundaries_are_preserved(final String code) {
    Assertions.assertDoesNotThrow(() -> HirTupleSyntaxValidationVisitorPass.pass(Inf.codeToHir(code)));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "(a: 1,) | Tuple value labels require '='",
    "(a = (b: 1,),) | Tuple value labels require '='",
    "val t: (a = int,) = (a = 1,) | Tuple type labels require ':'",
    "val t: (a: int) = (a = 1,) | Singleton named tuples require a trailing comma",
    "val f = (t: (a: int)) => t | Singleton named tuples require a trailing comma",
    "val f = (): (a: int) => (a = 1,) | Singleton named tuples require a trailing comma",
    "val t = ((a: 1),) | Singleton named tuples require a trailing comma",
    "val t: (a: int; b: int) = (a = 1, b = 2) | Singleton named tuples require a trailing comma"
  })
  void given__invalid_label_syntax__when__checked__then__explicit_diagnostic(final String code, final String message) {
    final var error = Assertions.assertThrows(
      IllegalArgumentException.class, () -> HirTupleSyntaxValidationVisitorPass.pass(Inf.codeToHir(code))
    );
    Assertions.assertEquals(message, error.getMessage());
  }
}
