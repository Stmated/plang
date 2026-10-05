package org.inf.llvm.lowering;

import org.inf.Inf;
import org.inf.InfRunOptions;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.mir.MirUnionValue;
import org.inf.ty.Ty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class MirCfgExecutionTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "val read = (t: (int, bool)) => t[0]; read(1, true)",
    "val read = (t: (int, bool)) => t[0]; read((1, 2))",
    "val read = (t: ((int, bool),)) => t[0][0]; val t = ((1, 2),); read t",
    "val make = (): (int, bool) => (1, 2); val t = make(); t[0]",
    "val make = (): (int, bool) => { return (1,); }; val t = make(); t[0]"
  })
  void given__incompatible_tuple_call_or_return__when__executed__then__rejected_before_native_execution(String code) {
    assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToResult(code));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val read = (t: (int, bool)) => t[0]; read((1, true), (2, false))",
    "val read = (t: (int, bool)) => t[0]; read()",
    "val read = (t: (int, bool), x: int) => x; val t = (1, true); read t",
    "val captured = (7,); val read = (t: (int, bool)) => captured[0]; read((1, true), (2, false))"
  })
  void given__incorrect_tuple_call_arity__when__executed__then__tuple_is_not_implicitly_spread(String code) {
    assertThrows(IllegalArgumentException.class, () -> Inf.codeToResult(code));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__tuple_bindings__when__reassigned__then__aliases_keep_the_original_reference(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(12, Inf.codeToResult("""
        var t: (int, bool) = (1, true);
        val original = t;
        t = (2, false);
        original[0] * 10 + t[0]
        """, options).resultValue()),
      () -> assertEquals(91, Inf.codeToResult("""
        val first = ([1],);
        val second = ([2],);
        var selected = first;
        val original = selected;
        selected = second;
        selected[0][0] = 9;
        second[0][0] * 10 + original[0][0]
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__tuples_in_aggregates__when__stored_and_replaced__then__references_and_nested_reads_are_preserved(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(12, Inf.codeToResult("""
        val t = ((1, true),);
        val values = [t, ((2, false),)];
        val original = values[0];
        values[0] = values[1];
        original[0][0] * 10 + values[0][0][0]
        """, options).resultValue()),
      () -> assertEquals(true, Inf.codeToResult("""
        val values = [(1, true), (2, false); (int, bool); 2];
        values[0][1]
        """, options).resultValue()),
      () -> assertEquals(12, Inf.codeToResult("""
        val S = struct { val value: ((int, bool),); };
        val t = ((1, true),);
        val instance = new heap S { value = t; };
        val original = instance.value;
        instance.value = ((2, false),);
        original[0][0] * 10 + instance.value[0][0]
        """, options).resultValue()),
      () -> assertEquals(9, Inf.codeToResult("""
        val S = struct { val value: ([;int;1],); };
        val t = ([1],);
        val instance = new heap S { value = t; };
        val values = [instance.value];
        values[0][0][0] = 9;
        t[0][0]
        """, options).resultValue()),
      () -> assertEquals(17, Inf.codeToResult("""
        val S = struct { val values: [;(int, bool);1]; };
        val instance = new heap S { values = [(7, true)]; };
        val counter = [0];
        val index = 0;
        val result = ({ counter[0] += 1; instance }).values[index][0];
        counter[0] * 10 + result
        """, options).resultValue()),
      () -> assertEquals(7, Inf.codeToResult("""
        val make = (x: int) => [(x, true)];
        val values = make(7);
        values[0][0]
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__tuple_parameters__when__called_with_either_syntax__then__tuple_is_one_argument(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(30, Inf.codeToResult("""
        val read = (t: (int, bool)) => t[0];
        val t = (10, true);
        val ordinary = read(t);
        val implicit = read t;
        ordinary + implicit + read((10, false))
        """, options).resultValue()),
      () -> assertEquals(10, Inf.codeToResult("""
        val read = (t: (int, bool)) => t[0];
        read(t: (10, true))
        """, options).resultValue()),
      () -> assertEquals(7, Inf.codeToResult("""
        val change = (t: ([;int;1],)): ([;int;1],) => { t[0][0] = 7; return t; };
        val t = ([1],);
        val returned = change t;
        t[0][0]
        """, options).resultValue()),
      () -> assertEquals(21, Inf.codeToResult("""
        val S = struct { val value: int; };
        val instance = new heap S { value = 1; };
        val change = (t: (S,)): (S,) => { t[0].value = 7; return t; };
        val t = (instance,);
        val returned = change(t);
        returned[0].value + instance.value + t[0].value
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__tuple_returns__when__callee_finishes__then__new_storage_remains_live(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(12, Inf.codeToResult("""
        val make = (x: int): (int, bool) => (x, true);
        val first = make(1);
        val second = make(2);
        first[0] * 10 + second[0]
        """, options).resultValue()),
      () -> assertEquals(12, Inf.codeToResult("""
        val make = (x: int) => { return (x, true); };
        val first = make(1);
        val second = make(2);
        first[0] * 10 + second[0]
        """, options).resultValue()),
      () -> assertEquals(12, Inf.codeToResult("""
        val make = (x: int): ((int, bool),) => { return ((x, true),); };
        val first = make(1);
        val second = make(2);
        first[0][0] * 10 + second[0][0]
        """, options).resultValue()),
      () -> assertEquals(7, Inf.codeToResult("""
        val make = () => (7,);
        val read = (t: (int,)) => t[0];
        read(make())
        """, options).resultValue()),
      () -> assertEquals(12, Inf.codeToResult("""
        val choose = (flag: bool): (int, bool) => {
          if (flag) { return (1, true); };
          (2, false)
        };
        val first = choose(true);
        val second = choose(false);
        first[0] * 10 + second[0]
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__lifted_tuple_functions__when__called__then__arguments_and_captures_keep_their_boundaries(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertEquals(25, Inf.codeToResult("""
      val captured = ([1],);
      val outer = (t: ((int, bool),)): ((int, bool),) => {
        val inner = (value: ((int, bool),)): ((int, bool),) => {
          captured[0][0] += 1;
          return value;
        };
        val ordinary = inner(t);
        inner ordinary
      };
      val argument = ((10, true),);
      val first = outer(argument);
      val second = outer argument;
      first[0][0] + second[0][0] + captured[0][0]
      """, options).resultValue());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__tuple_call_compositions__when__executed__then__effects_follow_source_order_exactly_once(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(23144, Inf.codeToResult("""
        val counter = [0];
        val bump = () => { counter[0] += 1; return counter[0]; };
        val make = () => (bump(), (bump(), true));
        val read = (t: (int, (int, bool)), x: int) => t[0] * 10000 + t[1][0] * 1000 + x * 100;
        val result = read(x: bump(), t: make());
        result + bump() * 10 + counter[0]
        """, options).resultValue()),
      () -> assertEquals(17, Inf.codeToResult("""
        val counter = [0];
        val read = (t: (int, bool), x: int) => t[0] + x;
        val run = () => read(({ counter[0] += 1; return 7; }, true), { counter[0] += 10; 2 });
        val result = run();
        counter[0] * 10 + result
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__positional_tuples__when__executed__then__scalar_slots_use_correct_layout(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(10, Inf.codeToResult("(10,)[0]", options).resultValue()),
      () -> assertEquals(10, Inf.codeToResult("val t: (int, bool) = (10, true); t[0]", options).resultValue()),
      () -> assertEquals(true, Inf.codeToResult("(10, true)[1]", options).resultValue()),
      () -> assertEquals(false, Inf.codeToResult("((10, true), (false,))[1][0]", options).resultValue()),
      () -> assertEquals(10, Inf.codeToResult("((10, true),)[0][0]", options).resultValue()),
      () -> assertEquals(20L, Inf.codeToResult("(true, 20L)[1]", options).resultValue()),
      () -> assertEquals(2.5f, Inf.codeToResult("(true, 2.5f)[1]", options).resultValue()),
      () -> assertEquals(2.5, Inf.codeToResult("(true, 2.5d)[1]", options).resultValue()),
      () -> assertEquals("hello", Inf.codeToResult("(true, \"hello\")[1]", options).resultValue()),
      () -> assertEquals(7, Inf.codeToResult("val t = (1u8, 7, true); t[1]", options).resultValue()),
      () -> assertEquals(true, Inf.codeToResult("(10, true)[1u8]", options).resultValue()),
      () -> assertEquals(true, Inf.codeToResult("(10, true)[0x1]", options).resultValue()),
      () -> assertEquals(true, Inf.codeToResult("(10, true)[0b1]", options).resultValue()),
      () -> assertEquals(true, Inf.codeToResult("(10, true)[001]", options).resultValue()),
      () -> assertEquals(true, Inf.codeToResult("(10, true)[0_1]", options).resultValue()),
      () -> assertEquals(true, Inf.codeToResult("(10, true)[1L]", options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__tuple_elements_with_side_effects__when__executed__then__left_to_right_exactly_once(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    final var code = """
      val bump = (a: [;int;1]) => { a[0] += 1; return a[0]; };
      val counter = [0];
      val t = (bump(counter), (bump(counter), bump(counter)), bump(counter));
      t[0] * 10000 + t[1][0] * 1000 + t[1][1] * 100 + t[2] * 10 + counter[0]
      """;
    assertEquals(12344, Inf.codeToResult(code, options).resultValue());
    assertEquals(1, Inf.codeToResult(
      "val values = [1]; val t = (values[0], { values[0] = 9; 20 }); t[0]", options).resultValue());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__tuple_alias__when__contained_array_is_mutated__then__shared_reference(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertEquals(9, Inf.codeToResult(
      "val values = [1]; val t = (values,); val alias = t; alias[0][0] = 9; t[0][0]", options).resultValue());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__nested_read_of_effectful_target__when__executed__then__target_evaluated_once(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertEquals(11, Inf.codeToResult("""
      val counter = [0];
      val result = ({ counter[0] += 1; ((counter[0], true),) })[0][0];
      counter[0] * 10 + result
      """, options).resultValue());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__tuple_element_exits__when__executed__then__later_elements_are_skipped(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(7, Inf.codeToResult("val f = () => (1, { return 7; }, 1 / 0); f()", options).resultValue()),
      () -> assertEquals(7, Inf.codeToResult("val f = () => ((1, { return 7; }), 1 / 0); f()", options).resultValue()),
      () -> assertEquals(37, Inf.codeToResult("""
        val f = (flag: bool) => {
          val t = ({ if (flag) { return 7; } 10 }, 20);
          t[0] + t[1]
        };
        f(true) + f(false)
        """, options).resultValue()),
      () -> assertEquals(17, Inf.codeToResult("""
        val counter = [0];
        val f = (a: [;int;1]) => ( { a[0] += 1; 10 }, { return 7; }, { a[0] += 10; 20 });
        val result = f(counter);
        counter[0] * 10 + result
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void explicitLocalsPreserveCopiesAndLoopUpdates(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertEquals(1, Inf.codeToResult("var a = 1; var b = a; a = 2; return b;", options).resultValue());
    assertEquals(12, Inf.codeToResult(
      "val next = (x: int) => x + 2; var a = 0; for (var i = 0; i < 6; i += 1) { a = next(a); } return a;", options).resultValue());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void nestedFunctionCfgAndEarlyReturns(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    final var code = """
      val choose = (x: int) => {
        if (x < 0) { return 5; }
        var a = 0;
        for (var i = 0; i < x; i += 1) {
          if (i < 2) { a += 1; } else { a += 5; }
        }
        return a;
      };
      choose(4) + choose(-1)
      """;
    assertEquals(17, Inf.codeToResult(code, options).resultValue());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void shortCircuitPreservesSideEffects(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    final var code = """
      val bump = (a: [;int;2]) => { a[0] += 1; return 1 == 1; };
      val values = [0, 0];
      val skippedAnd = (1 == 2) && bump(values);
      val skippedOr = (1 == 1) || bump(values);
      val evaluatedAnd = (1 == 1) && bump(values);
      return values[0];
      """;
    assertEquals(1, Inf.codeToResult(code, options).resultValue());
  }

  @Test
  void functionVariablesCanBeReassigned() {
    assertEquals(2, Inf.codeToResult("val one = () => 1; val two = () => 2; var fn = one; fn = two; fn()").resultValue());
  }

  @Test
  void taggedUnionResultsKeepTheirDiscriminant() {
    final var present = assertInstanceOf(MirUnionValue.class, Inf.codeToResult("if (1 == 1) 42").resultValue());
    assertEquals(Ty.INTEGER, present.type().types()[present.variant()]);
    assertEquals(42, present.payload());
    final var absent = assertInstanceOf(MirUnionValue.class, Inf.codeToResult("if (1 == 2) 42").resultValue());
    assertEquals(Ty.VOID, absent.type().types()[absent.variant()]);
    assertNull(absent.payload());
  }

  @Test
  void fieldInitializersEvaluateInSourceOrderAndAssignmentSharesTheReference() {
    final var code = """
      val S = struct { val a: int; val b: int; };
      val bump = (a: [;int;2]) => { a[0] += 1; return a[0]; };
      val counter = [0, 0];
      val instance = new heap S { b = bump(counter); a = bump(counter); };
      val alias = instance;
      alias.b = 3;
      return instance.a * 10 + instance.b;
      """;
    assertEquals(23, Inf.codeToResult(code).resultValue());
  }
}
