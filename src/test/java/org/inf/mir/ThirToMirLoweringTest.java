package org.inf.mir;

import org.inf.Inf;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.exceptions.UnreachableCodeException;
import org.inf.execution.interpreter.InterpreterCodeExecutor;
import org.inf.hir.Hir;
import org.inf.mir.model.MirNode;
import org.inf.thir.raising.HirToThirRaising;
import org.inf.ty.Ty;
import org.inf.ty.TyPointer;
import org.inf.ty.TyStruct;
import org.inf.ty.TyUnion;
import org.inf.ty.TyValueNumberInteger;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.TypeComparison;
import org.junit.jupiter.api.Test;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ThirToMirLoweringTest {

  @ParameterizedTest
  @ValueSource(strings = {
    "val n = 7;",
    "var n: int;",
    "var n = 7; n = 8;",
    "var n = 7; n += 1;",
    "val use = () => 7;"
  })
  void given__terminal_script_assignment_or_declaration__when__lowered__then__signature_and_return_are_void(
    final String code
  ) {
    final var module = Inf.codeToMir(code);
    final var ret = assertInstanceOf(Mir.Return.class, module.initNode().terminator());
    assertAll(
      () -> assertEquals(Ty.VOID, module.script().signature().returnType()),
      () -> assertSame(Mir.Unit.INSTANCE, ret.value())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "if (flag) { return 7; }; true",
    "if (flag) { return 7; }; val n = true;",
    "if (flag) { return 7; }",
    "flag && { return 7; }",
    "flag || { return 7; }"
  })
  void given__script_returns_and_fallthrough__when__lowered__then__both_paths_return_the_inferred_union(
    final String body
  ) {
    final var module = Inf.codeToMir("val flag = false; %s".formatted(body));
    final var returns = module.script().blocks().stream().map(block -> block.terminator())
      .filter(Mir.Return.class::isInstance).map(Mir.Return.class::cast).toList();
    assertAll(
      () -> assertInstanceOf(TyUnion.class, module.script().signature().returnType()),
      () -> assertEquals(2, returns.size()),
      () -> assertTrue(returns.stream().allMatch(ret -> ret.value().ty().equals(module.script().signature().returnType())))
    );
  }

  @Test
  void given__noncontinuing_script_without_returns__when__lowered__then__no_normal_return_is_emitted() {
    final var root = new Hir.Program(new Hir.Loop(new Hir.LoopContinue()));
    final var thir = new HirToThirRaising(new MachineTarget(64)).raise(root);
    final var module = ThirToMirLowering.lower(thir);
    assertAll(
      () -> assertEquals(Ty.DEADEND, root.ty()),
      () -> assertEquals(Ty.DEADEND, root.expressions().ty()),
      () -> assertEquals(Ty.VOID, module.script().signature().returnType()),
      () -> assertTrue(module.script().blocks().stream().noneMatch(block -> block.terminator() instanceof Mir.Return))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val n = 7",
    "var n: int",
    "var n = 7; n = 8",
    "var n = 7; n += 1"
  })
  void given__terminal_assignment_or_declaration__when__function_is_lowered__then__signature_and_return_are_void(
    final String body
  ) {
    final var module = Inf.codeToMir("val use = () => { %s; }; use()".formatted(body));
    final var use = module.functions().stream().filter(function -> function != module.script()).findFirst().orElseThrow();
    final var returns = use.blocks().stream().map(block -> block.terminator())
      .filter(Mir.Return.class::isInstance).map(Mir.Return.class::cast).toList();
    assertAll(
      () -> assertEquals(Ty.VOID, use.signature().returnType()),
      () -> assertEquals(Ty.VOID, module.script().signature().returnType()),
      () -> assertEquals(1, returns.size()),
      () -> assertSame(Mir.Unit.INSTANCE, returns.getFirst().value())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "{ if (flag) { return 7; }; true }",
    "flag && { return 7; }",
    "flag || { return 7; }"
  })
  void given__explicit_return_and_boolean_fallthrough__when__lowered__then__both_paths_return_the_inferred_union(
    final String body
  ) {
    final var module = Inf.codeToMir("val use = (flag: bool) => %s; use(false)".formatted(body));
    final var use = module.functions().stream().filter(function -> function != module.script()).findFirst().orElseThrow();
    final var returns = use.blocks().stream().map(block -> block.terminator())
      .filter(Mir.Return.class::isInstance).map(Mir.Return.class::cast).toList();
    assertAll(
      () -> assertInstanceOf(TyUnion.class, use.signature().returnType()),
      () -> assertEquals(2, returns.size()),
      () -> assertTrue(returns.stream().allMatch(ret -> ret.value().ty().equals(use.signature().returnType()))),
      () -> assertEquals(use.signature().returnType(), module.script().signature().returnType())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "(b = 2, a = 1)",
    "new heap S { b = 2; a = 1; }"
  })
  void given__named_tuple_or_struct_spread__when__lowered__then__field_order_is_bound_to_parameter_order(final String value) {
    final var module = Inf.codeToMir("""
      val S = struct { val b: int; val a: int; };
      val args = %s;
      val pair = (a: int, b: int) => a;
      pair(...args)
      """.formatted(value));
    final var instructions = instructions(module);
    final var loads = instructions.stream().filter(Mir.Load.class::isInstance).map(Mir.Load.class::cast)
      .filter(load -> load.place() instanceof Mir.Field).toList();
    final var call = instructions.stream().filter(Mir.Call.class::isInstance).map(Mir.Call.class::cast)
      .findFirst().orElseThrow();
    assertEquals(2, loads.size());
    assertAll(
      () -> assertEquals(1, instructions.stream().filter(Mir.NewStruct.class::isInstance).count()),
      () -> assertEquals(0, assertInstanceOf(Mir.Field.class, loads.get(0).place()).index()),
      () -> assertEquals(1, assertInstanceOf(Mir.Field.class, loads.get(1).place()).index()),
      () -> assertEquals(2, call.arguments().size())
    );
    for (var i = 0; i < loads.size(); i++) {
      final var argument = call.arguments().get(1 - i);
      final var loadedValue = loads.get(i).result();
      if (argument != loadedValue) {
        final var conversion = instructions.stream().filter(Mir.Convert.class::isInstance).map(Mir.Convert.class::cast)
          .filter(convert -> convert.result() == argument).findFirst().orElseThrow();
        assertSame(loadedValue, conversion.value());
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "pair(...(if ({ return true; }) then args else args))",
    "pair(...(if ({ return true; }) then s else s))",
    "pair(...(make({ return true; })))",
    "pair(...(new heap S { b = 2; a = { return true; }; }))",
    "pair(...({ return true; }, 2u8))",
    "pair(...({ return true; }))"
  })
  void given__noncontinuing_spread__when__lowered__then__no_hypothetical_aggregate_or_call_is_emitted(final String call) {
    final var code = """
      val S = struct { val b: uint8; val a: uint8; };
      val args = (b = 2u8, a = 1u8);
      val s = new heap S { b = 2; a = 1; };
      val make = (flag: bool): S => s;
      val pair = (a: uint8, b: uint8) => a;
      val use = () => %s;
      use()
      """.formatted(call);
    final var module = Inf.codeToMir(code);
    final var use = module.functions().stream()
      .filter(function -> function != module.script() && function.signature().returnType() == Ty.BOOLEAN)
      .findFirst().orElseThrow();
    final var instructions = use.blocks().stream().flatMap(block -> block.instructions().stream()).toList();
    assertAll(
      () -> assertEquals(Ty.BOOLEAN, module.script().signature().returnType()),
      () -> assertEquals(Ty.BOOLEAN, use.signature().returnType()),
      () -> assertEquals(0, instructions.stream().filter(Mir.Call.class::isInstance).count()),
      () -> assertEquals(0, instructions.stream().filter(Mir.NewStruct.class::isInstance).count())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f = (p: uint8) => p; f(1)",
    "val Fn = (value: uint8): uint8; val f: Fn = (p) => p; f(1)",
    "val Fn = (value: uint8): uint8; val outer: Fn = (p) => { val inner = () => p; inner() }; outer(1)",
    "val outer = (captured: uint8) => { val f = (p: uint8) => p + captured; f(1) }; outer(2)"
  })
  void given__resolved_parameters__when__lowered__then__signatures_values_and_locals_retain_binding_types(final String code) {
    final var module = Inf.codeToMir(code);
    final var parameterValues = module.functions().stream().flatMap(function -> function.blocks().stream())
      .flatMap(block -> block.instructions().stream()).filter(Mir.Parameter.class::isInstance)
      .map(Mir.Parameter.class::cast).toList();
    assertFalse(parameterValues.isEmpty());
    for (final var function : module.functions()) {
      for (final var parameter : function.signature().parameters()) {
        assertTrue(parameter.ty() instanceof TyValueNumberInteger integer
          && integer.width().value() == 8 && !integer.signed());
        if (!function.external()) {
          final var local = function.locals().stream().filter(value -> value.name().equals(parameter.name()))
            .findFirst().orElseThrow();
          assertEquals(parameter.ty(), local.ty());
        }
      }
    }
    for (final var parameter : parameterValues) {
      assertTrue(parameter.result().ty() instanceof TyValueNumberInteger integer
        && integer.width().value() == 8 && !integer.signed());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val n: uint8 = 7; n",
    "val n = 7u8; n",
    "var n: uint8 = 7; n = 8; n"
  })
  void given__scalar_declaration__when__lowered__then__locals_and_stores_use_the_binding_type(final String code) {
    final var module = Inf.codeToMir(code);
    final var locals = module.functions().stream().flatMap(function -> function.locals().stream())
      .filter(local -> local.name().equals("n")).toList();
    assertEquals(1, locals.size());
    final var local = locals.getFirst();
    final var stores = instructions(module).stream().filter(Mir.Store.class::isInstance)
      .map(Mir.Store.class::cast).filter(store -> store.place() == local).toList();
    assertAll(
      () -> assertTrue(local.ty() instanceof TyValueNumberInteger integer
        && integer.width().value() == 8 && !integer.signed()),
      () -> assertFalse(stores.isEmpty()),
      () -> assertTrue(stores.stream().allMatch(store -> store.value().ty().equals(local.ty())))
    );
  }

  @Test
  void given__standalone_declaration__when__lowered__then__local_allocation_uses_the_binding_type() {
    final var module = Inf.codeToMir("var n: uint8; 7");
    final var locals = module.functions().stream().flatMap(function -> function.locals().stream())
      .filter(local -> local.name().equals("n")).toList();
    assertEquals(1, locals.size());
    assertTrue(locals.getFirst().ty() instanceof TyValueNumberInteger integer
      && integer.width().value() == 8 && !integer.signed());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "s.fn(5)",
    "s.fn(...(5,))"
  })
  void given__captured_lambda_in_function_field__when__lowered__then__unsafe_signature_conversion_is_rejected(final String expression) {
    final var code = """
      val Fn = (v: int): int;
      val S = struct { val fn: Fn; };
      val offset = 2;
      val s = new heap S { fn = (v) => v * 2 + offset; };
      %s
      """.formatted(expression);
    final var error = assertThrows(IllegalArgumentException.class, () -> Inf.codeToMir(code));
    assertTrue(error.getMessage().startsWith("Cannot convert incompatible function signatures:"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "pair(...1)",
    "pair(...[1, 2])",
    "pair(...(1,))",
    "pair(...(1, 2, 3))",
    "pair(...(a = 1, b = 2), unknown = 3)",
    "pair(a = 1, a = 2, ...(b = 3,))",
    "pair(...(a = 1, b = 2), a = 3, a = 4)",
    "pair(args = ...(1, 2))"
  })
  void given__invalid_spread_binding__when__lowered__then__compilation_fails(final String expression) {
    assertThrows(IllegalArgumentException.class, () ->
      Inf.codeToMir("val pair = (a: int, b: int) => a * 10 + b; %s".formatted(expression)));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "pair((1, 2))",
    "val args = (1, 2); pair(args)"
  })
  void given__unspread_tuple__when__passed_to_scalar_parameters__then__tuple_boundary_is_preserved(final String expression) {
    assertThrows(InvalidTypeConversionException.class, () ->
      Inf.codeToMir("val pair = (a: int, b: int) => a * 10 + b; %s".formatted(expression)));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f = (t: (p1: uint8, p2: uint8)) => t; val args = ((1u8, 2u8),); f(...args)",
    "val f = (t: (int, int)) => t; f(...((1, true),), t = (1, 2))"
  })
  void given__incompatible_spread_tuple_slot__when__lowered__then__existing_layouts_and_overridden_values_are_checked(final String code) {
    assertThrows(InvalidTypeConversionException.class, () -> Inf.codeToMir(code));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "pair(...(1))",
    "pair(...[1, 2])"
  })
  void given__non_aggregate_spread__when__typed__then__shape_diagnostic_is_reported(final String expression) {
    final var error = assertThrows(IllegalArgumentException.class, () ->
      Inf.codeToThir("val pair = (a: int, b: int) => a + b; %s".formatted(expression)));
    assertTrue(error.getMessage().contains("Spreading requires a statically known tuple or struct"));
  }

  @Test
  void given__existing_spread_reference__when__lowered__then__the_original_aggregate_is_not_copied() {
    final var module = Inf.codeToMir("""
      val pair = (a: int, b: int) => a + b;
      val args = (1, 2);
      pair(...args)
      """);
    final var instructions = instructions(module);
    final var fields = instructions.stream().filter(Mir.Load.class::isInstance)
      .map(Mir.Load.class::cast).map(Mir.Load::place).filter(Mir.Field.class::isInstance)
      .map(Mir.Field.class::cast).toList();
    assertAll(
      () -> assertEquals(1, instructions.stream().filter(Mir.NewStruct.class::isInstance).count()),
      () -> assertEquals(2, fields.size())
    );
    assertSame(fields.get(0).target(), fields.get(1).target());
  }

  @Test
  void given__spread_varargs_and_overrides__when__lowered__then__only_bound_arguments_are_emitted_and_promoted() {
    final var module = Inf.codeToMir("""
      val external = (a: int, ...): int;
      external(...(a = 2, ignored = true), a = 1, ...(255u8, true, 1.0f))
      """);
    final var call = instructions(module).stream().filter(Mir.Call.class::isInstance)
      .map(Mir.Call.class::cast).findFirst().orElseThrow();
    assertAll(
      () -> assertEquals(4, call.arguments().size()),
      () -> assertEquals(call.signature().parameters()[0].ty(), call.arguments().get(0).ty()),
      () -> assertEquals(Ty.INTEGER, call.arguments().get(1).ty()),
      () -> assertEquals(Ty.INTEGER, call.arguments().get(2).ty()),
      () -> assertEquals(Ty.DOUBLE, call.arguments().get(3).ty())
    );
  }

  @Test
  void given__named_fixed_argument_and_varargs__when__lowered__then__vararg_promotions_are_preserved() {
    final var module = Inf.codeToMir("""
      val external = (a: int, ...): int;
      external(a = 1, 255u8, true, 1.0f)
      """);
    final var call = instructions(module).stream().filter(Mir.Call.class::isInstance)
      .map(Mir.Call.class::cast).findFirst().orElseThrow();
    assertAll(
      () -> assertTrue(call.signature().vararg()),
      () -> assertEquals(4, call.arguments().size()),
      () -> assertEquals(call.signature().parameters()[0].ty(), call.arguments().get(0).ty()),
      () -> assertEquals(Ty.INTEGER, call.arguments().get(1).ty()),
      () -> assertEquals(Ty.INTEGER, call.arguments().get(2).ty()),
      () -> assertEquals(Ty.DOUBLE, call.arguments().get(3).ty()),
      () -> assertEquals(0, instructions(module).stream().filter(Mir.NewStruct.class::isInstance).count())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f = () => 7; f()",
    "val f = (a: int) => a; f(a = 7)",
    "val f = (a: int) => a; f a = 7",
    "val f = (a: int, b: int) => a + b; f(b = 2, a = 5)",
    "val f = (a: int, b: int) => a + b; f b = 2, a = 5",
    "val outer = (captured: int) => { val f = (a: int) => a + captured; f(a = 2) }; outer(5)"
  })
  void given__ordinary_argument_lists__when__lowered__then__no_runtime_tuple_is_allocated(final String code) {
    final var module = Inf.codeToMir(code);
    assertEquals(0, module.functions().stream().flatMap(function -> function.blocks().stream())
      .flatMap(block -> block.instructions().stream()).filter(Mir.NewStruct.class::isInstance).count());
  }

  @Test
  void given__reordered_fresh_tuple__when__lowered__then__one_allocation_receives_destination_order() {
    final var module = Inf.codeToMir("""
      val t: (a: uint8, bool, b: uint16) = (true, b = 255u8, a = 1);
      t.a
      """);
    final var instructions = instructions(module);
    final var allocations = instructions.stream().filter(Mir.NewStruct.class::isInstance)
      .map(Mir.NewStruct.class::cast).toList();
    assertEquals(1, allocations.size());
    final var allocation = allocations.getFirst();
    final var type = assertInstanceOf(TyStruct.class, MirTypes.pointee(allocation.result().ty()));
    final var conversion = instructions.stream().filter(Mir.Convert.class::isInstance)
      .map(Mir.Convert.class::cast).findFirst().orElseThrow();
    assertAll(
      () -> assertEquals("a", type.fields()[0].name()),
      () -> assertNull(type.fields()[1].name()),
      () -> assertEquals("b", type.fields()[2].name()),
      () -> assertEquals("1", assertInstanceOf(Mir.Constant.class, allocation.fields().get(0)).content()),
      () -> assertEquals("true", assertInstanceOf(Mir.Constant.class, allocation.fields().get(1)).content()),
      () -> assertSame(conversion.result(), allocation.fields().get(2)),
      () -> assertTrue(instructions.indexOf(conversion) < instructions.indexOf(allocation)),
      () -> assertInstanceOf(TyValueNumberInteger.class, conversion.value().ty())
    );
  }

  @Test
  void given__named_tuple_and_struct_views__when__lowered__then__compatibility_does_not_allocate_copies() {
    final var module = Inf.codeToMir("""
      val S = struct { val a: int; val b: bool; };
      val original = (a = 1, b = true);
      val instance: S = original;
      val tuple: (a: int, b: bool) = instance;
      tuple.a + tuple[0]
      """);
    final var instructions = instructions(module);
    final var allocation = assertInstanceOf(Mir.NewStruct.class, instructions.stream()
      .filter(Mir.NewStruct.class::isInstance).findFirst().orElseThrow());
    final var type = assertInstanceOf(TyStruct.class, MirTypes.pointee(allocation.result().ty()));
    assertAll(
      () -> assertTrue(type.tuple()),
      () -> assertEquals("a", type.fields()[0].name()),
      () -> assertEquals("b", type.fields()[1].name()),
      () -> assertEquals(1, instructions.stream().filter(Mir.NewStruct.class::isInstance).count()),
      () -> assertTrue(instructions.stream().filter(Mir.Convert.class::isInstance).map(Mir.Convert.class::cast)
        .allMatch(convert -> TypeComparison.sameValueType(convert.value().ty(), convert.result().ty())))
    );
  }

  @Test
  void given__tuple_stored_in_aggregates__when__lowered__then__storage_does_not_allocate_tuple_copies() {
    final var module = Inf.codeToMir("""
      val t = (1, true);
      val S = struct { val value: (int, bool); };
      val instance = new heap S { value = t; };
      val values = [instance.value];
      values[0][0]
      """);
    final var allocations = instructions(module).stream().filter(Mir.NewStruct.class::isInstance)
      .map(Mir.NewStruct.class::cast).toList();
    assertAll(
      () -> assertEquals(2, allocations.size()),
      () -> assertEquals(1, allocations.stream().filter(allocation ->
        MirTypes.pointee(allocation.result().ty()) instanceof TyStruct tuple && tuple.hasUnnamedFields()).count()),
      () -> assertEquals(1, instructions(module).stream().filter(Mir.NewArray.class::isInstance).count())
    );
  }

  @Test
  void given__out_of_order_struct_initializers__when__lowered__then__source_evaluation_order_and_resolved_field_order_are_distinct() {
    final var module = Inf.codeToMir("""
      val S = struct { val first: int; val second: int; };
      val evaluate = (v: int): int => v;
      val s = new heap S { second = evaluate(2); first = evaluate(1); };
      s.first
      """);
    final var operations = instructions(module);
    final var calls = operations.stream().filter(Mir.Call.class::isInstance).map(Mir.Call.class::cast).toList();
    final var construction = operations.stream().filter(Mir.NewStruct.class::isInstance)
      .map(Mir.NewStruct.class::cast).findFirst().orElseThrow();
    assertAll(
      () -> assertEquals(2, calls.size()),
      () -> assertEquals("2", assertInstanceOf(Mir.Constant.class, calls.getFirst().arguments().getFirst()).content()),
      () -> assertEquals("1", assertInstanceOf(Mir.Constant.class, calls.getLast().arguments().getFirst()).content()),
      () -> assertSame(calls.getLast().result(), construction.fields().getFirst()),
      () -> assertSame(calls.getFirst().result(), construction.fields().getLast())
    );
  }

  @Test
  void given__contextual_tuple_widening__when__lowered__then__scalar_slots_are_converted_before_allocation() {
    final var module = Inf.codeToMir("""
      val x = 255u8;
      val inner = ([1],);
      val t: (int16, ([;int;1],)) = (x, inner);
      t[0]
      """);
    final var instructions = instructions(module);
    final var allocations = instructions.stream().filter(Mir.NewStruct.class::isInstance)
      .map(Mir.NewStruct.class::cast).toList();
    final var construction = allocations.getLast();
    final var type = assertInstanceOf(TyStruct.class, MirTypes.pointee(construction.result().ty()));
    final var conversion = instructions.stream().filter(Mir.Convert.class::isInstance)
      .map(Mir.Convert.class::cast)
      .filter(convert -> convert.result() == construction.fields().getFirst()).findFirst().orElseThrow();
    assertAll(
      () -> assertEquals(2, allocations.size()),
      () -> assertEquals(type.fields()[0].ty(), conversion.result().ty()),
      () -> assertTrue(instructions.indexOf(conversion) < instructions.indexOf(construction)),
      () -> assertTrue(instructions.stream().filter(Mir.Convert.class::isInstance)
        .map(Mir.Convert.class::cast).allMatch(convert ->
          TypeComparison.sameValueType(convert.value().ty(), convert.result().ty())
            || convert.value().ty() instanceof TyValueNumberInteger)),
      () -> assertInstanceOf(Mir.Value.class, construction.fields().getLast())
    );
  }

  @Test
  void given__tuple_parameter_and_return__when__lowered__then__only_the_original_tuple_is_allocated() {
    final var module = Inf.codeToMir("""
      val identity = (value: (int, bool)): (int, bool) => value;
      var first: (int, bool) = (10, true);
      val second = identity(first);
      first = second;
      first[0]
      """);
    final var function = module.functions().stream().filter(candidate -> candidate != module.script()).findFirst().orElseThrow();
    final var call = instructions(module).stream().filter(Mir.Call.class::isInstance).map(Mir.Call.class::cast).findFirst().orElseThrow();
    final var type = function.signature().returnType();
    final var pointer = assertInstanceOf(TyPointer.class, type);
    final var tuple = assertInstanceOf(TyStruct.class, pointer.inner());
    assertAll(
      () -> assertTrue(tuple.hasUnnamedFields()),
      () -> assertEquals(type, function.signature().parameters()[0].ty()),
      () -> assertEquals(type, call.result().ty()),
      () -> assertEquals(1, call.arguments().size()),
      () -> assertEquals(type, call.arguments().getFirst().ty()),
      () -> assertEquals(1, module.functions().stream().flatMap(candidate -> candidate.blocks().stream())
        .flatMap(block -> block.instructions().stream()).filter(Mir.NewStruct.class::isInstance).count())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "identity(args)",
    "identity(value = args)",
    "identity value = args"
  })
  void given__struct_reference_argument__when__lowered__then__no_argument_aggregate_is_allocated(final String invocation) {
    final var module = Inf.codeToMir("""
      val S = struct { val a: int; };
      val identity = (value: S): S => value;
      val args = new heap S { a = 7; };
      val result = %s;
      result.a
      """.formatted(invocation));
    final var call = instructions(module).stream().filter(Mir.Call.class::isInstance)
      .map(Mir.Call.class::cast).findFirst().orElseThrow();
    assertAll(
      () -> assertEquals(1, call.arguments().size()),
      () -> assertEquals(call.signature().parameters()[0].ty(), call.arguments().getFirst().ty()),
      () -> assertEquals(1, module.functions().stream().flatMap(function -> function.blocks().stream())
        .flatMap(block -> block.instructions().stream()).filter(Mir.NewStruct.class::isInstance).count())
    );
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "(1,) | 1",
    "(a = 1,) | 1",
    "(a = 1, true) | 1",
    "(a = (b = 1,), true) | 2",
    "(1, true) | 1",
    "((1, true), (2,)) | 3"
  })
  void given__typed_tuple_value__when__lowered__then__uses_existing_aggregate_instructions(String code, int allocations) {
    final var module = Inf.codeToMir(code);
    assertEquals(allocations, instructions(module).stream().filter(Mir.NewStruct.class::isInstance).count());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "(1,)[0]",
    "(a = 1,)[0]",
    "(a = 1,).a",
    "(a = (b = 1,),).a.b",
    "(1, true)[1]",
    "((1, true),)[0][1]"
  })
  void given__tuple_read__when__lowered__then__uses_field_places(String code) {
    final var module = Inf.codeToMir(code);
    final var loads = instructions(module).stream().filter(Mir.Load.class::isInstance).map(Mir.Load.class::cast).toList();
    assertAll(
      () -> assertFalse(loads.isEmpty()),
      () -> assertTrue(loads.stream().allMatch(load -> load.place() instanceof Mir.Field))
    );
  }

  @Test
  void given__tuple_alias__when__lowered__then__stores_same_reference_without_copying_slots() {
    final var module = Inf.codeToMir("val t = (1, true); val alias = t; alias[0]");
    final var instructions = instructions(module);
    final var allocation = instructions.stream().filter(Mir.NewStruct.class::isInstance).map(Mir.NewStruct.class::cast).findFirst().orElseThrow();
    final var stores = instructions.stream().filter(Mir.Store.class::isInstance).map(Mir.Store.class::cast).toList();
    final var aliasLoad = instructions.stream().filter(Mir.Load.class::isInstance).map(Mir.Load.class::cast)
      .filter(load -> load.place() == stores.getFirst().place()).findFirst().orElseThrow();
    assertAll(
      () -> assertEquals(1, instructions.stream().filter(Mir.NewStruct.class::isInstance).count()),
      () -> assertSame(allocation.result(), stores.getFirst().value()),
      () -> assertSame(aliasLoad.result(), stores.getLast().value())
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "val f = () => (1, { return 7; }, 1 / 0); f()",
    "val f = () => ((1, { return 7; }), 1 / 0); f()"
  })
  void given__nonreturning_tuple_element__when__lowered__then__no_allocation_or_later_evaluation(String code) {
    final var module = Inf.codeToMir(code);
    final var instructions = module.functions().stream().flatMap(function -> function.blocks().stream())
      .flatMap(block -> block.instructions().stream()).toList();
    assertAll(
      () -> assertEquals(7, run(code)),
      () -> assertTrue(instructions.stream().noneMatch(instruction -> instruction instanceof Mir.NewStruct || instruction instanceof Mir.Binary))
    );
  }

  private Object run(String code) {
    return new InterpreterCodeExecutor().execute(Inf.codeToMir(code).initNode());
  }

  private List<Mir.Instruction> instructions(MirLoweringResult module) {
    return module.script().blocks().stream().flatMap(block -> block.instructions().stream()).toList();
  }

  @Test
  void given__conversion_operand_exits__when__lowered__then__conversion_is_not_emitted() {
    final var hir = assertInstanceOf(Hir.Program.class, Inf.codeToHir("return 7"));
    hir.expressions(new Hir.Convert(hir.expressions(), Ty.LONG));
    final var typed = new HirToThirRaising(new MachineTarget(64)).raise(hir);
    final var module = ThirToMirLowering.lower(typed);
    assertAll(
      () -> assertEquals(Ty.INTEGER, module.script().signature().returnType()),
      () -> assertTrue(instructions(module).stream().noneMatch(Mir.Convert.class::isInstance)),
      () -> assertInstanceOf(Mir.Return.class, module.initNode().terminator())
    );
  }

  @Test
  void arithmeticHasValuesAndSeparateTerminator() {
    final var module = Inf.codeToMir("1 + 1");
    final var entry = module.initNode();
    final var binary = assertInstanceOf(Mir.Binary.class, entry.instructions().getFirst());
    final var ret = assertInstanceOf(Mir.Return.class, entry.terminator());
    assertSame(binary.result(), ret.value());
    assertTrue(entry.successors().isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> entry.instructions().clear());
  }

  @ParameterizedTest
  @CsvSource({
    "return 7;",
    "if (1 == 1) 42",
    "val x = 1;",
    "[1, 2]",
    "val fn = (x: int) => x + 1; fn"
  })
  void scriptSignatureUsesCompletedThirType(final String code) {
    final var thir = Inf.codeToThir(code);
    final var expected = MirTypes.returnType(thir.root().ty());
    final var module = ThirToMirLowering.lower(thir);
    assertEquals(expected, module.script().signature().returnType(), code);
  }

  @Test
  void assigningScalarCopiesItsValueRatherThanItsStorage() {
    final var code = "var a = 1; var b = a; a = 2; return b;";
    final var module = Inf.codeToMir(code);
    assertEquals(2, module.script().locals().size());
    assertEquals(1, run(code));
    final var stores = instructions(module).stream().filter(Mir.Store.class::isInstance).map(Mir.Store.class::cast).toList();
    assertSame(stores.get(0).place(), stores.get(2).place());
    assertNotSame(stores.get(0).place(), stores.get(1).place());
  }

  @Test
  void nestedConditionalsUseActualFallthroughBlocks() {
    final var code = "var a = 0; if (1 == 1) { if (2 == 2) { a = 10; } else { a = 20; } a += 1; } else { a = 30; } return a;";
    final var module = Inf.codeToMir(code);
    assertEquals(11, run(code));
    assertTrue(module.script().blocks().stream().allMatch(block -> block.terminator() != null));
    assertEquals(2, module.script().blocks().stream().filter(block -> block.terminator() instanceof Mir.Branch).count());
  }

  @Test
  void loopReadsAndWritesStableLocalsAcrossIterations() {
    assertEquals(45, run("var a = 0; for (var i = 0; i < 10; i += 1) { a += i; } return a;"));
    assertEquals(12, run("var a = 0; for (var i = 0; i < 4; i += 1) { if (i < 2) { a += 1; } else { a += 5; } } return a;"));
  }

  @Test
  void sequentialLoopsDoNotLeakTheInsertionPoint() {
    assertEquals(6, run("var a = 0; for (var i = 0; i < 2; i += 1) { a += 1; } for (var j = 0; j < 2; j += 1) { a += 2; } return a;"));
  }

  @Test
  void generatedUpdatesAreSkippedOnlyOnNonContinuingPaths() {
    assertEquals(7, run("for (var i = 0; i < 3; i += 1) { return 7; } return 9;"));
    assertEquals(7, run("for (var i = 0; i < 3; i += 1) { if (i == 0) { return 7; } else { return 8; } } return 9;"));
    assertEquals(1, run("for (var i = 0; i < 3; i += 1) { if (i == 1) { return i; } } return 9;"));
  }

  @Test
  void shadowedDeclarationsHaveDistinctStorage() {
    assertEquals(1, run("var a = 1; { var a = 2; a = 3; } return a;"));
  }

  @Test
  void returningArmDoesNotSupplyAnOptionalVariant() {
    assertEquals(7, run("val fn = (x: int) => { val y = if (x == 0) { return 7; } else 8; return y; }; fn(0)"));
    assertEquals(8, run("val fn = (x: int) => { val y = if (x == 0) { return 7; } else 8; return y; }; fn(1)"));
  }

  @Test
  void missingElseProducesTaggedVoidVariant() {
    final var present = assertInstanceOf(MirUnionValue.class, run("if (1 == 1) 42"));
    assertEquals(Ty.INTEGER, present.type().types()[present.variant()]);
    assertEquals(42, present.payload());
    final var absent = assertInstanceOf(MirUnionValue.class, run("if (1 == 2) 42"));
    assertEquals(Ty.VOID, absent.type().types()[absent.variant()]);
    assertNull(absent.payload());
    assertInstanceOf(TyUnion.class, Inf.codeToMir("if (1 == 1) 42").script().signature().returnType());
  }

  @ParameterizedTest
  @CsvSource({
    "(1 == 2) && (1 / 0 == 0),false",
    "(1 == 1) || (1 / 0 == 0),true",
    "(1 == 1) && (2 == 2),true",
    "(1 == 2) || (2 == 3),false"
  })
  void shortCircuitSkipsDangerousRightOperand(String code, boolean expected) {
    assertEquals(expected, run(code));
  }

  @Test
  void callResultsAreStoredOnEveryIteration() {
    assertEquals(3, run("val next = (x: int) => x + 1; var a = 0; for (var i = 0; i < 3; i += 1) { a = next(a); } return a;"));
  }

  @Test
  void functionDefinitionsAreNotExecutableInstructions() {
    final var module = Inf.codeToMir("val add = (a: int, b: int) => a + b; add(1, 2)");
    assertEquals(2, module.functions().size());
    assertEquals(3, run("val add = (a: int, b: int) => a + b; add(1, 2)"));
  }

  @Test
  void liftedFunctionsCaptureDynamicCallableBindingsByDeclaration() {
    assertEquals(2, run("""
      val one = () => 1;
      val two = () => 2;
      var selected = one;
      val invoke = () => selected();
      selected = two;
      invoke()
      """));
  }

  @Test
  void typeNamesAreNotRuntimeCaptures() {
    assertEquals(7, run("""
      val S = struct { val value: int; };
      val make = () => new heap S { value = 7; };
      val instance = make();
      instance.value
      """));
  }

  @Test
  void assignmentOfArrayCopiesTheReference() {
    assertEquals(9, run("var a = [1, 2]; var b = a; b[0] = 9; return a[0];"));
  }

  @Test
  void readsHappenBeforeLaterArgumentSideEffects() {
    final var code = """
      val bump = (a: [;int;2]) => { a[0] = 9; return 2; };
      val first = (a: int, b: int) => a;
      val values = [1, 2];
      first(values[0], bump(values))
      """;
    assertEquals(1, run(code));
  }

  @Test
  void namedArgumentsAreEvaluatedInSourceOrder() {
    final var code = """
      val bump = (a: [;int;2]) => { a[0] += 1; return a[0]; };
      val pair = (a: int, b: int) => a * 10 + b;
      val values = [0, 0];
      pair(b = bump(values), a = bump(values))
      """;
    assertEquals(21, run(code));
  }

  @Test
  void compoundAssignmentEvaluatesItsPlaceOnce() {
    final var code = """
      val next = (a: [;int;2]) => { a[0] += 1; return 0; };
      val counter = [0, 0];
      val values = [10];
      values[next(counter)] += 1;
      return values[0] * 10 + counter[0];
      """;
    assertEquals(111, run(code));
  }

  @Test
  void unreachableSourceStatementsAreRejectedDuringMirLowering() {
    assertThrows(UnreachableCodeException.class, () -> Inf.codeToMir("return 1; 2;"));
    assertThrows(UnreachableCodeException.class, () ->
      Inf.codeToMir("val f = (a: int) => a; val args = (2,); f(...({ return 1; args; }))"));
    assertThrows(UnreachableCodeException.class, () -> Inf.codeToMir("if (1 == 1) { return 1; } else { return 2; } 3;"));
    assertThrows(UnreachableCodeException.class, () -> Inf.codeToMir(
      "for (var i = 0; i < 3; i += 1) { return 7; 8; } return 9;"
    ));
  }

  @Test
  void blockTerminatorIsItsOnlySourceOfEdges() {
    final var block = new MirNode("entry");
    final var target = new MirNode("target");
    block.terminate(new Mir.Jump(target));
    assertEquals(List.of(target), block.successors());
    assertThrows(IllegalStateException.class, () -> block.terminate(new Mir.Return(Mir.Unit.INSTANCE)));
    assertThrows(IllegalStateException.class, () -> block.append(new Mir.Store(new Mir.Local(0, "x", Ty.INTEGER), new Mir.Constant("1", Ty.INTEGER))));
  }

  @Test
  void breakAndContinueTerminateTheirBlocks() {
    final var loop = new Hir.Loop(
      new Hir.Conditional(
        new Hir.Literal("true", Ty.BOOLEAN),
        new Hir.LoopBreak(null),
        new Hir.LoopContinue(),
        Ty.DEADEND
      ),
      Ty.VOID
    );
    final var root = new Hir.Program(new Hir.Expressions(new Hir.Expression[]{
      loop, new Hir.Return(new Hir.Literal("7", Ty.INTEGER))
    }, Ty.INTEGER));
    final var thir = new HirToThirRaising(new MachineTarget(64)).raise(root);
    final var module = ThirToMirLowering.lower(thir);
    assertEquals(7, new InterpreterCodeExecutor().execute(module.initNode()));
    assertEquals(1, module.script().blocks().stream().filter(block -> block.terminator() instanceof Mir.Branch).count());
  }

  @Test
  void given__loop_transfer_in_tuple__when__lowered__then__no_allocation_or_later_evaluation() {
    final var tuple = new Hir.Tuple(new Hir.TupleEntry[]{
      new Hir.TupleEntry(null, new Hir.Literal("1", Ty.INTEGER)),
      new Hir.TupleEntry(null, new Hir.Conditional(
        new Hir.Literal("true", Ty.BOOLEAN), new Hir.LoopBreak(null), new Hir.LoopContinue(), Ty.DEADEND
      )),
      new Hir.TupleEntry(null, new Hir.BinaryOperation(
        new Hir.Literal("1", Ty.INTEGER), Hir.BinaryOperationKind.DIVIDE, new Hir.Literal("0", Ty.INTEGER)
      ))
    }, null);
    final var root = new Hir.Program(new Hir.Expressions(new Hir.Expression[]{
      new Hir.Loop(tuple, Ty.VOID), new Hir.Return(new Hir.Literal("7", Ty.INTEGER))
    }, Ty.INTEGER));
    final var thir = new HirToThirRaising(new MachineTarget(64)).raise(root);
    final var module = ThirToMirLowering.lower(thir);
    assertAll(
      () -> assertEquals(7, new InterpreterCodeExecutor().execute(module.initNode())),
      () -> assertTrue(instructions(module).stream().noneMatch(instruction -> instruction instanceof Mir.NewStruct || instruction instanceof Mir.Binary))
    );
  }

  @Test
  void statementsAfterBreakOrContinueAreRejected() {
    for (final var transfer : List.of(new Hir.LoopBreak(null), new Hir.LoopContinue())) {
      final var body = new Hir.Expressions(new Hir.Expression[]{transfer, new Hir.Literal("1", Ty.INTEGER)}, Ty.INTEGER);
      final var root = new Hir.Program(new Hir.Loop(body, Ty.VOID));
      final var thir = new HirToThirRaising(new MachineTarget(64)).raise(root);
      assertThrows(UnreachableCodeException.class, () -> ThirToMirLowering.lower(thir));
    }
  }
}
