package org.inf.llvm.lowering;

import org.inf.Inf;
import org.inf.InfRunOptions;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.mir.MirUnionValue;
import org.inf.ty.Ty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class MirCfgExecutionTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "0 | true",
    "0 | false",
    "2 | true",
    "2 | false"
  })
  void given__script_return_and_fallthrough__when__executed__then__the_reached_union_variant_is_returned(
    final int optimization, final boolean flag
  ) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    final var booleanFallthrough = assertInstanceOf(MirUnionValue.class, Inf.codeToResult("""
      val flag = %s;
      if (flag) { return 7; };
      true
      """.formatted(flag), options).resultValue());
    final var voidFallthrough = assertInstanceOf(MirUnionValue.class, Inf.codeToResult("""
      val flag = %s;
      if (flag) { return 7; };
      val n = true;
      """.formatted(flag), options).resultValue());
    final var missingBranch = assertInstanceOf(MirUnionValue.class,
      Inf.codeToResult("if (%s) { return 7; }".formatted(flag), options).resultValue());
    final var shortCircuit = assertInstanceOf(MirUnionValue.class,
      Inf.codeToResult("%s && { return 7; }".formatted(flag), options).resultValue());
    assertAll(
      () -> assertEquals(flag ? Ty.INTEGER : Ty.BOOLEAN, booleanFallthrough.type().types()[booleanFallthrough.variant()]),
      () -> assertEquals(flag ? 7 : true, booleanFallthrough.payload()),
      () -> assertEquals(flag ? Ty.INTEGER : Ty.VOID, voidFallthrough.type().types()[voidFallthrough.variant()]),
      () -> assertEquals(flag ? 7 : null, voidFallthrough.payload()),
      () -> assertEquals(flag ? Ty.INTEGER : Ty.VOID, missingBranch.type().types()[missingBranch.variant()]),
      () -> assertEquals(flag ? 7 : null, missingBranch.payload()),
      () -> assertEquals(flag ? Ty.INTEGER : Ty.BOOLEAN, shortCircuit.type().types()[shortCircuit.variant()]),
      () -> assertEquals(flag ? 7 : false, shortCircuit.payload())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {
    0,
    2
  })
  void given__terminal_script_assignment_or_declaration__when__executed__then__the_result_is_void(final int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertNull(Inf.codeToResult("val n = 7;", options).resultValue()),
      () -> assertNull(Inf.codeToResult("var n: int;", options).resultValue()),
      () -> assertNull(Inf.codeToResult("var n = 7; n = 8;", options).resultValue()),
      () -> assertNull(Inf.codeToResult("var n = 7; n += 1;", options).resultValue()),
      () -> assertNull(Inf.codeToResult("val use = () => 7;", options).resultValue())
    );
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "0 | true",
    "0 | false",
    "2 | true",
    "2 | false"
  })
  void given__explicit_return_and_fallthrough__when__executed__then__the_reached_union_variant_is_returned(
    final int optimization, final boolean flag
  ) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    final var booleanFallthrough = assertInstanceOf(MirUnionValue.class, Inf.codeToResult("""
      val use = (flag: bool) => { if (flag) { return 7; }; true };
      use(%s)
      """.formatted(flag), options).resultValue());
    final var voidFallthrough = assertInstanceOf(MirUnionValue.class, Inf.codeToResult("""
      val use = (flag: bool) => { if (flag) { return 7; }; val n = true; };
      use(%s)
      """.formatted(flag), options).resultValue());
    final var shortCircuit = assertInstanceOf(MirUnionValue.class, Inf.codeToResult("""
      val use = (flag: bool) => flag && { return 7; };
      use(%s)
      """.formatted(flag), options).resultValue());
    assertAll(
      () -> assertEquals(flag ? Ty.INTEGER : Ty.BOOLEAN, booleanFallthrough.type().types()[booleanFallthrough.variant()]),
      () -> assertEquals(flag ? 7 : true, booleanFallthrough.payload()),
      () -> assertEquals(flag ? Ty.INTEGER : Ty.VOID, voidFallthrough.type().types()[voidFallthrough.variant()]),
      () -> assertEquals(flag ? 7 : null, voidFallthrough.payload()),
      () -> assertEquals(flag ? Ty.INTEGER : Ty.BOOLEAN, shortCircuit.type().types()[shortCircuit.variant()]),
      () -> assertEquals(flag ? 7 : false, shortCircuit.payload())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {
    0,
    2
  })
  void given__terminal_assignment__when__void_function_is_called__then__the_effect_is_preserved(final int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    final var result = Inf.codeToResult("""
      val values = [0];
      val use = () => { values[0] = 7; };
      use();
      values[0]
      """, options);
    assertEquals(7, result.resultValue());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__named_call_lists__when__executed__then__binding_and_capture_evaluation_are_preserved(final int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(22, Inf.codeToResult("""
        var a = 100;
        val pair = (a: int, b: int) => a * 10 + b;
        pair(b = 2, a = 1) + a / 10
        """, options).resultValue()),
      () -> assertEquals(21, Inf.codeToResult("""
        val pair = (a: int, b: int) => a * 10 + b;
        pair b = 1, a = 2
        """, options).resultValue()),
      () -> assertEquals(12, Inf.codeToResult("""
        val pair = (a: int, b: int) => a * 10 + b;
        pair(b = 2, 1)
        """, options).resultValue()),
      () -> assertEquals(13, Inf.codeToResult("""
        var a = 0;
        val identity = (a: int) => a;
        identity(a = { a = 1; a + 1; }) + a * 10 + 1
        """, options).resultValue()),
      () -> assertEquals(22, Inf.codeToResult("""
        val captured = [0];
        val outer = () => {
          val bump = () => { captured[0] += 1; captured[0] };
          val pair = (a: int, b: int) => a * 10 + b + captured[0];
          pair(b = bump(), a = bump())
        };
        outer() - 1
        """, options).resultValue()),
      () -> assertEquals(10, Inf.codeToResult("""
        val f = (t: (int, bool)) => if (t[1]) then t[0] else 0;
        f(t = (10, true))
        """, options).resultValue()),
      () -> assertEquals(7, Inf.codeToResult("""
        val f = (a: int, b: int) => a + b;
        val outer = () => f(a = { return 7; }, b = 100);
        outer()
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "0 | identity(args)",
    "2 | identity(args)",
    "0 | identity(value = args)",
    "2 | identity(value = args)",
    "0 | identity value = args",
    "2 | identity value = args"
  })
  void given__struct_reference_argument__when__executed__then__one_argument_preserves_the_value(
    final int optimization, final String invocation
  ) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertEquals(7, Inf.codeToResult("""
      val S = struct { val a: int; };
      val identity = (value: S): S => value;
      val args = new heap S { a = 7; };
      val result = %s;
      result.a
      """.formatted(invocation), options).resultValue());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__fresh_tuple_matching__when__executed__then__calls_and_bindings_use_destination_slots(final int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(6, Inf.codeToResult("""
        val fn4 = (t: (p1: uint8, p2: uint8)): uint16 => {
          return t.p1 + t.p2;
        };
        fn4((1, 2)) + fn4((p1 = 1, p2 = 2))
        """, options).resultValue()),
      () -> assertEquals(33, Inf.codeToResult("""
        val fn4 = (t: (p1: uint8, p2: uint8)): uint16 => t.p1 * 10 + t.p2;
        fn4((1, 2)) + fn4((p1 = 1, p2 = 2)) + fn4((p2 = 9, p1 = 0))
        """, options).resultValue()),
      () -> assertEquals(12, Inf.codeToResult("""
        val fn4 = (t: (p1: uint8, p2: uint8)) => t.p1 * 10 + t.p2;
        fn4 ((2, p1 = 1))
        """, options).resultValue()),
      () -> assertEquals(1256, Inf.codeToResult("""
        val t: (a: uint8, bool, b: uint16) = (true, b = 1000, a = 255);
        if (t[1]) then t.a + t.b + t[0] - 254 else 0
        """, options).resultValue()),
      () -> assertEquals(120, Inf.codeToResult("""
        var t: (a: int, b: int) = (1, 2);
        val original = t;
        t = (b = 4, 3);
        original.a * 100 + original.b * 10 + t.a + t.b - 7
        """, options).resultValue()),
      () -> assertEquals(256, Inf.codeToResult("""
        val t: (a: int16, b: uint16) = (b = 1u8, a = 255u8);
        t.a + t[1]
        """, options).resultValue()),
      () -> assertEquals(9, Inf.codeToResult("""
        val captured = [0];
        val outer = (): (a: uint8, b: uint8) => {
          val read = (t: (a: uint8, b: uint8)) => { captured[0] += 1; t };
          read((b = 4, 3))
        };
        val t = outer();
        t.a + t.b + captured[0] + 1
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__fresh_tuple_return_context__when__executed__then__nested_blocks_and_branches_match_recursively(final int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(123, Inf.codeToResult("""
        val make = (flag: bool): (a: uint8, nested: (x: uint8, y: uint16)) => {
          if (flag) { return (nested = (y = 3, 2), a = 1); };
          if (true) then (1, (2, 3)) else { (nested = (3, x = 2), 1) }
        };
        val first = make(true);
        val second = make(false);
        first.a * 100 + first.nested.x * 10 + second.nested.y
        """, options).resultValue()),
      () -> assertEquals(12, Inf.codeToResult("""
        val t: (a: uint8, b: uint16) = if (false) then (1, 2) else { (b = 2, 1) };
        t.a * 10 + t.b
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__reordered_tuple_effects__when__executed__then__source_order_and_early_exit_are_preserved(final int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(2313, Inf.codeToResult("""
        val counter = [0];
        val bump = () => { counter[0] += 1; counter[0] };
        val t: (a: int, b: int, c: int) = (c = bump(), bump(), b = bump());
        (t.a * 100 + t.b * 10 + t.c) * 10 + counter[0]
        """, options).resultValue()),
      () -> assertEquals(1221, Inf.codeToResult("""
        val counter = [0];
        val bump = () => { counter[0] += 1; counter[0] };
        val read = (x: int, t: (a: int, b: int)) => x * 100 + t.a * 10 + t.b;
        val result = read(t = (b = bump(), a = bump()), x = bump());
        result + counter[0] * 300
        """, options).resultValue()),
      () -> assertEquals(112, Inf.codeToResult("""
        val counter = [0];
        val make = (): (a: int, b: int, c: int) => (
          c = { counter[0] += 1; 3 },
          { return (c = 6, b = 5, a = 4); },
          b = { counter[0] += 100; 2 }
        );
        val t = make();
        counter[0] * 100 + t.a + t.b + t.c - 3
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__reordered_fresh_outer_tuple__when__references_are_mutated__then__nested_objects_remain_shared(final int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertEquals(36, Inf.codeToResult("""
      val S = struct { val a: int; };
      val original = (a = 1,);
      val object: S = original;
      val array = [2];
      val nested = (object = object,);
      val t: (nested: (object: S,), values: [;int;1]) = (values = array, nested);
      t.nested.object.a = 9;
      t.values[0] = 9;
      original.a + nested.object.a + array[0] + t[1][0]
      """, options).resultValue());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__named_and_mixed_tuples__when__executed__then__both_read_forms_preserve_slot_order(final int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(20, Inf.codeToResult("val t = (a = 10,); t.a + t[0]", options).resultValue()),
      () -> assertEquals(40, Inf.codeToResult("""
        val t: (a: int, bool, b: int) = (a = 10, true, b = 20);
        if (t[1]) then t.a + t[0] + t.b else 0
        """, options).resultValue()),
      () -> assertEquals(30, Inf.codeToResult("""
        val t = (10, a = (inner = 20,), false);
        t[0] + t.a.inner
        """, options).resultValue()),
      () -> assertEquals(20, Inf.codeToResult("""
        val t = (outer = (inner = 10,),);
        t.outer[0] + t[0].inner
        """, options).resultValue()),
      () -> assertEquals(256, Inf.codeToResult("""
        val t: (a: uint8, b: uint16) = (a = 255, b = 1u8);
        t.a + t[1]
        """, options).resultValue()),
      () -> assertEquals(109, Inf.codeToResult("""
        var a = 99;
        val t = (a = 10,);
        a + t.a
        """, options).resultValue()),
      () -> assertEquals(20, Inf.codeToResult("""
        var foo = 0;
        val t = (a = { foo = 10; foo; },);
        foo + t.a
        """, options).resultValue()),
      () -> assertEquals(2, Inf.codeToResult("var a = 1; (a = 2); a", options).resultValue()),
      () -> assertEquals(3, Inf.codeToResult("val t = (int = 1, bool = 2); t.int + t.bool", options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__named_tuple_function_boundaries__when__executed__then__calls_returns_and_captures_stay_separate(final int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(9, Inf.codeToResult("""
        val make = (x: int): (a: int, b: bool) => (a = x, b = true);
        val read = (t: (a: int, b: bool)) => if (t.b) then t.a else 0;
        val t = make(3);
        val first = read t;
        first + read(t = make(6))
        """, options).resultValue()),
      () -> assertEquals(9, Inf.codeToResult("""
        val make = (flag: bool): (a: uint8,) => {
          if (flag) { return (a = 3,); };
          (a = 6,)
        };
        val first = make(true);
        val second = make(false);
        first.a + second[0]
        """, options).resultValue()),
      () -> assertEquals(17, Inf.codeToResult("""
        val captured = [0];
        val outer = (): (captured: int, b: int) => {
          val identity = (t: (captured: int, b: int)): (captured: int, b: int) => { captured[0] += 1; t };
          identity((captured = 3, b = 4))
        };
        val t = outer();
        captured[0] * 10 + t.captured + t.b
        """, options).resultValue()),
      () -> assertEquals(15, Inf.codeToResult("""
        val f = (x: int, t: (a: int,)) => x + t.a;
        f(t = (a = 10,), x = 5)
        """, options).resultValue()),
      () -> assertEquals(3, Inf.codeToResult("""
        val f = (a: int, b: int) => a + b;
        f(b = 2, a = 1)
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__named_tuple_storage__when__executed__then__nested_references_and_reassignment_are_preserved(final int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(13, Inf.codeToResult("""
        var t = (a = 10,);
        val original = t;
        t = (a = 3,);
        original.a + t[0]
        """, options).resultValue()),
      () -> assertEquals(30, Inf.codeToResult("""
        val S = struct { val tuple: (a: int,); };
        val s = new heap S { tuple = (a = 10,); };
        val values = [s.tuple, (a = 20,)];
        s.tuple = values[1];
        values[0].a + s.tuple.a
        """, options).resultValue()),
      () -> assertEquals(9, Inf.codeToResult("""
        val t = (values = [1],);
        val alias = t;
        alias.values[0] = 9;
        t[0][0]
        """, options).resultValue()),
      () -> assertEquals(9, Inf.codeToResult("""
        val S = struct { val a: int; };
        val s = new heap S { a = 1; };
        val t = (object = s,);
        t.object.a = 9;
        s.a
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__exact_layout_tuple_and_struct__when__interchanged__then__the_same_object_is_shared(final int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(18, Inf.codeToResult("""
        val S = struct { val a: int; val b: bool; };
        val tuple = (a = 1, b = true);
        val s: S = tuple;
        s.a = 9;
        val again: (a: int, b: bool) = s;
        tuple.a + again[0]
        """, options).resultValue()),
      () -> assertEquals(18, Inf.codeToResult("""
        val S = struct { val a: int; };
        val s = new heap S { a = 1; };
        val t: (a: int,) = s;
        s.a = 9;
        t.a + t[0]
        """, options).resultValue()),
      () -> assertEquals(12, Inf.codeToResult("""
        val S = struct { val a: int; };
        val asStruct = (t: (a: int,)): S => t;
        val asTuple = (s: S): (a: int,) => { return s; };
        val read = (s: S) => s.a;
        val s = asStruct((a = 3,));
        val t = asTuple(s);
        read(t) + read((a = 4,)) + t.a + asTuple((a = 2,)).a
        """, options).resultValue()),
      () -> assertEquals(9, Inf.codeToResult("""
        val Inner = struct { val a: int; };
        val Outer = struct { val inner: Inner; };
        val t = (inner = (a = 1,),);
        val s: Outer = t;
        s.inner.a = 9;
        t.inner.a
        """, options).resultValue()),
      () -> assertEquals(7, Inf.codeToResult("""
        val S = struct { val a: int; };
        val s = new heap S { a = 1; };
        val t = (a = 2,);
        val values = [s, t];
        values[1].a = 7;
        t.a
        """, options).resultValue()),
      () -> assertEquals(3, Inf.codeToResult("""
        val S = struct { val a: int; };
        val make = (flag: bool) => {
          if (flag) { return (a = 1,); };
          new heap S { a = 2; }
        };
        val first = make(true);
        val second = make(false);
        first.a + second.a
        """, options).resultValue()),
      () -> assertEquals(2, Inf.codeToResult("""
        val S = struct { val a: int; };
        val counter = [0];
        val make = (x: int) => {
          counter[0] += 1;
          if (x == 1) { return (a = 1,); };
          if (x == 2) { return new heap S { a = 2; }; };
        };
        make(1);
        make(2);
        counter[0]
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__named_tuple_flow__when__executed__then__source_order_and_early_exit_are_preserved(final int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(1233, Inf.codeToResult("""
        val counter = [0];
        val bump = () => { counter[0] += 1; counter[0] };
        val t = (b = bump(), bump(), a = bump());
        (t.b * 100 + t[1] * 10 + t.a) * 10 + counter[0]
        """, options).resultValue()),
      () -> assertEquals(17, Inf.codeToResult("""
        val counter = [0];
        val make = () => (a = { counter[0] += 1; 1 }, { return 7; }, b = { counter[0] += 100; 3 });
        val result = make();
        counter[0] * 10 + result
        """, options).resultValue()),
      () -> assertEquals(12, Inf.codeToResult("""
        val counter = [0];
        val make = (flag: bool): (a: int, b: int) => {
          val t = (a = { if (flag) { return (a = 1, b = 2); }; 3 }, b = { counter[0] += 1; 4 });
          t
        };
        val first = make(true);
        val second = make(false);
        counter[0] + first.a + first.b + second.a + second.b + 1
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__contextual_tuple_literals__when__executed__then__bindings_calls_and_returns_use_expected_layout(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(2554, Inf.codeToResult("""
        var t: (uint8, uint8) = (255, 2);
        val original = t;
        t = (3, 4);
        original[0] * 10 + t[1]
        """, options).resultValue()),
      () -> assertEquals(10, Inf.codeToResult("""
        val read = (t: (uint8, uint8)) => t[0] + t[1];
        val first = read((1, 2));
        val second = read(t = { (3, 4) });
        first + second
        """, options).resultValue()),
      () -> assertEquals(6, Inf.codeToResult("""
        val make = (): (uint8, uint8) => (1, 2);
        val read = (t: (uint8, uint8)) => t[0] + t[1];
        val t = make();
        val implicit = read t;
        implicit + read(make())
        """, options).resultValue()),
      () -> assertEquals(10, Inf.codeToResult("""
        val make = (flag: bool): (uint8, uint8) => {
          if (flag) { return (1, 2); };
          (3, 4)
        };
        val first = make(true);
        val second = make(false);
        first[0] + first[1] + second[0] + second[1]
        """, options).resultValue()),
      () -> assertEquals(127, Inf.codeToResult("val t: (int8,) = (127,); t[0]", options).resultValue()),
      () -> assertEquals(-128, Inf.codeToResult("val t: (int8,) = (-128,); t[0]", options).resultValue()),
      () -> assertEquals(153, Inf.codeToResult("val t: (uint8,) = (0x99,); t[0]", options).resultValue()),
      () -> assertEquals(255, Inf.codeToResult("val t: (uint8,) = (0b1111_1111,); t[0]", options).resultValue()),
      () -> assertEquals(255, Inf.codeToResult("val t: (uint8,) = (25_5,); t[0]", options).resultValue()),
      () -> assertEquals(Long.MAX_VALUE, Inf.codeToResult(
        "val t: (int64,) = (9223372036854775807,); t[0]", options).resultValue()),
      () -> assertEquals(Long.MIN_VALUE, Inf.codeToResult(
        "val t: (int64,) = (-9223372036854775808,); t[0]", options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__contextual_tuple_elements__when__widened__then__signed_and_unsigned_values_are_preserved(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(255, Inf.codeToResult("val x = 255u8; val t: (int16,) = (x,); t[0]", options).resultValue()),
      () -> assertEquals(-128, Inf.codeToResult("val x = -128i8; val t: (int16,) = (x,); t[0]", options).resultValue()),
      () -> assertEquals(255, Inf.codeToResult("val x = 255u8; val t: (uint16,) = (x,); t[0]", options).resultValue()),
      () -> assertEquals(300L, Inf.codeToResult("val t: (int64,) = (100 + 200,); t[0]", options).resultValue()),
      () -> assertEquals(255, Inf.codeToResult("""
        val source = (255u8,);
        val t: (int16,) = (source[0],);
        t[0]
        """, options).resultValue()),
      () -> assertEquals(255, Inf.codeToResult("""
        val captured = 255u8;
        val make = (): (int16,) => (captured,);
        val t = make();
        t[0]
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__nested_contextual_tuples__when__executed__then__fresh_layouts_and_shared_references_are_preserved(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(258, Inf.codeToResult("""
        val make = (): ((uint8, uint8), int16) => { return ((1, 2), 255u8); };
        val read = (t: ((uint8, uint8), int16)) => t[0][0] + t[0][1] + t[1];
        read(make())
        """, options).resultValue()),
      () -> assertEquals(9, Inf.codeToResult("""
        val inner = ([1],);
        val t: (int16, ([;int;1],)) = (255u8, inner);
        t[1][0][0] = 9;
        inner[0][0]
        """, options).resultValue()),
      () -> assertEquals(17, Inf.codeToResult("""
        val captured = [0];
        val outer = (): (uint8, uint8) => {
          val inner = (t: (uint8, uint8)): (uint8, uint8) => { captured[0] += 1; t };
          inner((3, 4))
        };
        val result = outer();
        captured[0] * 10 + result[0] + result[1]
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void given__contextual_tuple_flow__when__executed__then__effects_run_once_in_source_order(int optimization) {
    final var options = InfRunOptions.builder().optLevel(optimization).build();
    assertAll(
      () -> assertEquals(23414, Inf.codeToResult("""
        val counter = [0];
        val bump = (): uint8 => { counter[0] += 1; counter[0] };
        val read = (t: (uint16, (int16, uint8)), x: int) => t[0] * 1000 + t[1][0] * 100 + t[1][1] * 10 + x;
        val result = read(x = bump(), t = (bump(), (bump(), bump())));
        result * 10 + counter[0]
        """, options).resultValue()),
      () -> assertEquals(120, Inf.codeToResult("""
        val counter = [0];
        val make = (flag: bool): (uint8, uint8) => {
          val t: (uint8, uint8) = if (flag) then { counter[0] += 1; (1, 2) }
            else { counter[0] += 10; (3, 4) };
          t
        };
        val first = make(true);
        val second = make(false);
        counter[0] * 10 + first[0] + first[1] + second[0] + second[1]
        """, options).resultValue()),
      () -> assertEquals(12, Inf.codeToResult("""
        val counter = [0];
        val make = (): (uint8, uint8) => (
          { counter[0] += 1; 1 },
          { return (2, 3); },
          { counter[0] += 100; 4 }
        );
        val t = make();
        counter[0] * 10 + t[0]
        """, options).resultValue()),
      () -> assertEquals(15, Inf.codeToResult("""
        val counter = [0];
        val make = (flag: bool): (uint8, uint8) => {
          val t: (uint8, uint8) = ({ if (flag) { return (1, 2); }; 3 }, 4);
          t
        };
        val first = make(true);
        val second = make(false);
        first[0] + first[1] + second[0] + second[1] + 5
        """, options).resultValue())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
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
    "val read = (t: (int, bool)) => t[0]; read(1, true)",
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
        read(t = (10, true))
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
        val result = read(x = bump(), t = make());
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
