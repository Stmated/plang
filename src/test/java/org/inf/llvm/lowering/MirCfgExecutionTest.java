package org.inf.llvm.lowering;

import org.inf.Inf;
import org.inf.InfRunOptions;
import org.inf.mir.MirUnionValue;
import org.inf.ty.Ty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class MirCfgExecutionTest {

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
