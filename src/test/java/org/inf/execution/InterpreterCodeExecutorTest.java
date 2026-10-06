package org.inf.execution;

import org.inf.Inf;
import org.inf.execution.interpreter.InterpreterCodeExecutor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InterpreterCodeExecutorTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "pair(...(1, 2)) | 12",
    "val args = (1, 2); pair ...args | 12",
    "pair(...(b = 2, a = 1)) | 12",
    "pair(...(a = 1, 2)) | 12",
    "pair(1, ...(2,)) | 12",
    "pair(...(1,), 2) | 12",
    "pair(...(unused = true, a = 1, b = 2)) | 12",
    "pair(...(a = 3, b = 2), a = 1) | 12",
    "pair(...(3, 2), a = 1) | 12",
    "pair(3, ...(a = 1, b = 2)) | 12",
    "pair(a = 3, ...(a = 1, b = 2)) | 12",
    "pair(...(a = 3, b = 4), ...(a = 1, b = 2)) | 12",
    "val make = () => (1, 2); pair(...make()) | 12",
    "val make = () => (1, 2); pair ...make() | 12",
    "val values = [(1, 2)]; pair(...values[0]) | 12",
    "val service = (call = pair,); service.call(...(1, 2)) | 12",
    "val service = (call = pair,); service.call ...(1, 2) | 12",
    "val S = struct { val b: int; val unused: bool; val a: int; }; val args = new heap S { b = 2; unused = true; a = 1; }; pair(...args) | 12",
    "val S = struct { val args: (int, int); }; val source = new heap S { args = (1, 2); }; pair ...source.args | 12",
    "val outer = (captured: int) => { val f = (a: int, b: int) => a * 10 + b + captured; f(...(1, 2)); }; outer(5) | 17",
    "val identity = (t: (int, int)) => t[0] * 10 + t[1]; identity((1, 2)) | 12",
    "val zero = () => 7; zero(...(ignored = 1,)) | 7",
    "val read = (t: (a: uint8, b: uint8), x: int) => t.a * 10 + t.b + x; read(...((b = 2, a = 1), 0)) | 12"
  })
  void given__spread_arguments__when__executed__then__confirmed_binding_rules_are_used(final String expression, final int expected) {
    final var code = "val pair = (a: int, b: int) => a * 10 + b; %s".formatted(expression);
    Assertions.assertEquals(expected, new InterpreterCodeExecutor().execute(Inf.codeToMir(code).initNode()));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "val trace = [0]; val make = () => { trace[0] = trace[0] * 10 + 1; (a = 3, b = 2); }; val result = pair(...make(), a = { trace[0] = trace[0] * 10 + 2; 1; }); trace[0] * 100 + result | 1212",
    "val S = struct { val a: int; }; val args = new heap S { a = 1; }; pair(...args, b = { args.a = 9; 2; }) | 12",
    "var trace = 0; val result = pair(...(unused = { trace = 1; true; }, a = 3, b = 2), a = { trace = trace * 10 + 2; 1; }); trace * 100 + result | 1212",
    "val call = () => { pair(...{ return 7; }); }; call() | 7",
    "val call = () => { pair(...(1, { return 7; })); }; call() | 7",
    "val read = (value: (int,)) => value[0]; val inner = (7,); read(...(inner,)) | 7",
    "val S = struct { val a: int; }; val inner = new heap S { a = 1; }; val read = (value: S, b: int) => value.a * 10 + b; read(...(inner,), b = { inner.a = 9; 2; }) | 92",
    "val trace = [0]; val target = () => { trace[0] = trace[0] * 10 + 1; pair; }; val result = (target())(...{ trace[0] = trace[0] * 10 + 2; (1, 2); }); trace[0] * 100 + result | 1212",
    "val trace = [0]; val call = () => { pair(...{ return 7; }, b = { trace[0] = 9; 2; }); }; val result = call(); result + trace[0] | 7"
  })
  void given__spread_side_effects__when__executed__then__evaluation_and_field_capture_follow_source_order(final String expression, final int expected) {
    final var code = "val pair = (a: int, b: int) => a * 10 + b; %s".formatted(expression);
    Assertions.assertEquals(expected, new InterpreterCodeExecutor().execute(Inf.codeToMir(code).initNode()));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "val Fn = (v: int): int; val S = struct { val fn: Fn }; val s = new heap S { fn = (v) => v * 2 }; s.fn(5) | 10",
    "val Fn = (v: int): int; val S = struct { val fn: Fn; }; val s = new heap S { fn = (v) => v * 2; }; s.fn v = 5 | 10",
    "val Fn = (v: int): int; val S = struct { val fn: Fn; }; val s = new heap S { fn = (x) => x * 2; }; s.fn(v = 5) | 10",
    "val Fn = (v: int): int; val S = struct { val fn: Fn; }; val s = new heap S { fn = (v) => v * 2; }; s.fn(...(5,)) | 10",
    "val Fn = (v: int): int; val S = struct { val fn: Fn; }; val s = new heap S { fn = (v) => v; }; s.fn = (v) => v * 2; s.fn(5) | 10",
    "val Fn = (v: int): int; val S = struct { val fn: Fn; }; val s = new heap S { fn = { (v) => v * 2 }; }; val outer = (inner = s,); outer.inner.fn(5) | 10",
    "val Fn = (): int; val S = struct { val fn: Fn; }; val s = new heap S { fn = () => 7; }; s.fn() | 7"
  })
  void given__call_on_instance_field__when__called__then__expect_proper_result(final String code, final int expected) {
    Assertions.assertEquals(expected, new InterpreterCodeExecutor().execute(Inf.codeToMir(code).initNode()));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "val service = (call = pair,); service.call(1, 2) | 12",
    "val service = (call = pair,); service.call b = 2, a = 1 | 12",
    "val a = 1; val b = 2; val service = (call = pair, a = 9, b = 9); service.call(b = b, a = a) | 12",
    "val service = (call = pair,); service.call((1, 2)[0], 2) | 12",
    "val service = (inner = (call = pair,),); service.inner.call(1, 2) | 12",
    "val service = (calls = [pair],); (service.calls[0])(1, 2) | 12",
    "val services = [(call = pair,)]; services[0].call(1, 2) | 12",
    "val service = (make = () => (call = pair,),); service.make().call(1, 2) | 12",
    "val service = (make = () => pair,); (service.make())(1, 2) | 12",
    "val read = (t: (int, int)) => t[0] * 10 + t[1]; val service = (call = read,); service.call((1, 2)) | 12"
  })
  void given__function_valued_tuple_field__when__called__then__ordinary_argument_binding_is_used(
    final String expression, final int expected
  ) {
    final var code = "val pair = (a: int, b: int) => a * 10 + b; %s".formatted(expression);
    Assertions.assertEquals(expected, new InterpreterCodeExecutor().execute(Inf.codeToMir(code).initNode()));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "val trace = [0]; val target = () => { trace[0] = trace[0] * 10 + 1; (call = (a: int, b: int) => a * 10 + b,); }; val result = target().call(b = { trace[0] = trace[0] * 10 + 2; 2; }, a = { trace[0] = trace[0] * 10 + 3; 1; }); trace[0] * 100 + result | 12312",
    "val Fn = (v: int): int; val S = struct { val fn: Fn; }; val s = new heap S { fn = (v) => v * 2; }; s.fn({ s.fn = (v) => v * 3; 5; }) | 10",
    "val trace = [0]; val call = () => { val service = (fn = (a: int, b: int) => a + b,); service.fn({ return 7; }, { trace[0] = 9; 5; }); }; val result = call(); result + trace[0] | 7"
  })
  void given__field_call_side_effects__when__executed__then__target_precedes_arguments_and_each_runs_once(
    final String code, final int expected
  ) {
    Assertions.assertEquals(expected, new InterpreterCodeExecutor().execute(Inf.codeToMir(code).initNode()));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "service.call() | Missing function argument",
    "service.call(1) | Missing function argument",
    "service.call(1, 2, 3) | Too many arguments for function",
    "service.call(a = 1, a = 2) | Duplicate or excess argument",
    "service.call(other = 1, b = 2) | Unknown argument label: other",
    "service.missing(1, 2) | Unknown field: missing"
  })
  void given__invalid_field_call__when__compiled__then__ordinary_call_errors_are_reported(
    final String expression, final String expected
  ) {
    final var code = "val pair = (a: int, b: int) => a * 10 + b; val service = (call = pair,); %s".formatted(expression);
    final var error = Assertions.assertThrows(IllegalArgumentException.class, () -> Inf.codeToMir(code));
    Assertions.assertEquals(expected, error.getMessage());
  }

  @Test
  void testArithmetic() {
    final var executor = new InterpreterCodeExecutor();

    Assertions.assertEquals(3, executor.execute(Inf.codeToMir("1 + 2").initNode()));
    Assertions.assertEquals(3L, executor.execute(Inf.codeToMir("1L + 2L").initNode()));
    Assertions.assertEquals(3.5d, executor.execute(Inf.codeToMir("1d + 2.5d").initNode()));
    Assertions.assertEquals(3.5f, executor.execute(Inf.codeToMir("1f + 2.5f").initNode()));

    Assertions.assertEquals(2L, executor.execute(Inf.codeToMir("3L - 1L").initNode()));
    Assertions.assertEquals(6, executor.execute(Inf.codeToMir("3 * 2").initNode()));
    Assertions.assertEquals(5, executor.execute(Inf.codeToMir("10 / 2").initNode()));
    Assertions.assertEquals(40.0, executor.execute(Inf.codeToMir("20 / 0.5").initNode()));
  }

  @Test
  void testLossyArithmetic() {
    final var executor = new InterpreterCodeExecutor();

    Assertions.assertEquals(0, executor.execute(Inf.codeToMir("3 / 6").initNode()));
    Assertions.assertEquals(2, executor.execute(Inf.codeToMir("20 / 11").initNode()));
    Assertions.assertEquals(2, executor.execute(Inf.codeToMir("20 / 12").initNode()));
  }

  @Test
  void testArithmeticWithDifferentTypes() {
    final var executor = new InterpreterCodeExecutor();

    Assertions.assertEquals(3.5d, executor.execute(Inf.codeToMir("1d + 2.5f").initNode()));
  }

  @Test
  void unsignedShortValuesRemainPositiveWhenLoadedAndWidened() {
    final var executor = new InterpreterCodeExecutor();
    Assertions.assertEquals(65535, executor.execute(Inf.codeToMir("val x: uint16 = 65535; x + 0").initNode()));
    Assertions.assertEquals(65535, executor.execute(Inf.codeToMir("val a = [65535;uint16;1]; a[0] + 0").initNode()));
  }

  @Test
  void testFunctionCall() {
    final var executor = new InterpreterCodeExecutor();

    final var mir = Inf.codeToMir("val add = (a: int, b: int) => a + b; add(1, 2)");
    final var res = executor.execute(mir.initNode());
    Assertions.assertEquals(3, res);
  }
}
