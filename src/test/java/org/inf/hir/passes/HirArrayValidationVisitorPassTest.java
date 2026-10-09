package org.inf.hir.passes;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class HirArrayValidationVisitorPassTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "val v: [;2] = [10, 20]; v[1] | 20",
    "val make = (): [;2] => [10, 20]; (make())[1] | 20",
    "val Fn = (arr: [;int;2]): int; val read: Fn = (arr) => arr[1]; read([10, 20]) | 20",
    "var value: int = 1; val S = struct { val values: [;int;2]; }; val s = new heap S { values = { value = 7; [value, 20] }; }; s.values[0] | 7"
  })
  void given__resolved_array_inference__when__executed__then__declared_layout_and_result_agree(
    final String code, final int expected
  ) {
    Assertions.assertEquals(expected, Inf.codeToResult(code).resultValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val v: [;int;2] = [10, 20, 30]; v",
    "var v: [;int;2] = [10, 20]; v = [10, 20, 30]; v",
    "val use = (arr: [;int;2]) => arr[1]; use([10, 20, 30])",
    "val use = (arr: [;int;2]) => arr[1]; use(arr = [10, 20, 30])",
    "val use = (arr: [;int;2]) => arr[1]; use(...([10, 20, 30],))",
    "val S = struct { val arr: [;int;2]; }; val s = new heap S { arr = [10, 20, 30]; }; s.arr",
    "var arr: [;int;2] = [10, 20]; val S = struct { val arr: [;int;2]; }; new heap S { arr = { arr = [10, 20, 30]; [10, 20] }; }",
    "val S = struct { val arr: [;int;2]; }; val s = new heap S { arr = [10, 20]; }; s.arr = [10, 20, 30]; s.arr",
    "val make = (): [;int;2] => [10, 20, 30]; make()",
    "val make = (): [;int;2] => { return [10, 20, 30]; }; make()",
    "val make = (): [;2] => [10, 20, 30]; make()",
    "val make = (): [;2] => { return [10, 20, 30]; }; make()",
    "val make = (flag: bool): [;int;2] => if (flag) then [10, 20] else [10, 20, 30]; make(true)",
    "val make = (flag: bool): [;int;2] => { if (flag) { return [10, 20]; }; [10, 20, 30] }; make(true)",
    "val make = (): [;int;2] => true; make()",
    "val v: [;bool;2] = [10, 20]; v",
    "val use = (arr: [;bool;2]) => arr[1]; use([10, 20])",
    "val v: [;[;int;2];1] = [[10, 20, 30]]; v"
  })
  void given__incompatible_array_value__when__a_typed_boundary_is_validated__then__rejected(final String code) {
    Assertions.assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToThir(code));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val v: [;2] = [10, 20]; v[1]",
    "val v: [;int;2] = [10, 20]; v[1]",
    "var v: [;int;2] = [10, 20]; v = [30, 40]; v[1]",
    "val use = (arr: [;int;2]) => arr[1]; use([10, 20])",
    "val use = (arr: [;int;2]) => arr[1]; use(arr = [10, 20])",
    "val use = (arr: [;int;2]) => arr[1]; use(...([10, 20],))",
    "val S = struct { val arr: [;int;2]; }; val s = new heap S { arr = [10, 20]; }; s.arr[1]",
    "val make = (): [;int;2] => [10, 20]; (make())[1]",
    "val make = (): [;2] => [10, 20]; (make())[1]",
    "val make = (flag: bool): [;int;2] => { if (flag) { return [10, 20]; }; [30, 40] }; (make(true))[1]",
    "val use = (arr: [;int;]) => arr[1]; use([10, 20, 30])",
    "val make = (): [;int;] => [10, 20, 30]; (make())[1]",
    "val Arr = [;uint8;2]; val Fn = (): Arr; val make: Fn = (): [;2] => [10, 20]; (make())[1]"
  })
  void given__compatible_array_value__when__a_typed_boundary_is_validated__then__lowering_succeeds(final String code) {
    Assertions.assertDoesNotThrow(() -> Inf.codeToMir(code));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val use = (arr: [;int;2]) => arr[1]; val f = () => use({ return true; }); f()"
  })
  void given__noncontinuing_array_value__when__validated__then__no_value_is_required(final String code) {
    Assertions.assertDoesNotThrow(() -> Inf.codeToThir(code));
  }
}
