package org.inf.hir.passes;

import org.inf.Inf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HirFunctionSignatureValidationVisitorPassTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "val Fn = (value): bool; Fn",
    "val Fn = (value): bool; 1",
    "val Fn = (value): bool; val a: Fn = (value: uint8) => true; a",
    "val Fn = (value): bool; val a: Fn = (value: uint8) => true; val b: Fn = (value: bool) => value; b",
    "val Complete = (value: uint8): bool; val Fn: Complete = (value): bool; Fn",
    "val Fn = (arr: [;2]): int; Fn",
    "val Complete = (arr: [;int;2]): int; val Fn: Complete = (arr: [;2]): int; Fn",
    "val f = (value) => true; f",
    "val f = (arr: [;2]) => arr[1]; f",
    "val read = (arr: [;2]) => arr[1]; read([10, 20])"
  })
  void given__unresolved_signature__when__the_pipeline_runs__then__declaration_is_rejected(final String code) {
    final var error = Assertions.assertThrows(IllegalArgumentException.class, () -> Inf.codeToThir(code));
    Assertions.assertTrue(error.getMessage().contains("Function signature requires resolved parameter and return types"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val Fn = (value: uint8): bool; val f: Fn = (x) => true; f(10)",
    "val Fn = (value: uint8): bool; val f: Fn = (x: uint8) => true; f(10)",
    "val Fn = (arr: [;int;2]): int; val f: Fn = (arr) => arr[1]; f([10, 20])",
    "val Fn = (arr: [;int;2]): int; val f: Fn = (arr: [;int;2]) => arr[1]; f([10, 20])",
    "val Fn = (value: uint8): uint8; val Factory = (): Fn; val f: Factory = () => ((x) => x); (f())(10)",
    "val Fn = (value: int): int; val use = (fn: Fn) => fn(10); use((x) => x)",
    "val f = (value: uint8) => value; f(10)"
  })
  void given__complete_expected_signature__when__parameters_are_omitted_or_matching__then__lowering_succeeds(final String code) {
    Assertions.assertDoesNotThrow(() -> Inf.codeToMir(code));
  }
}
