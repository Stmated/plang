package org.inf.mir;

import org.inf.Inf;
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
        Ty.DEADEND, null
      ),
      Ty.VOID, null
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
        new Hir.Literal("true", Ty.BOOLEAN), new Hir.LoopBreak(null), new Hir.LoopContinue(), Ty.DEADEND, null
      )),
      new Hir.TupleEntry(null, new Hir.BinaryOperation(
        new Hir.Literal("1", Ty.INTEGER), Hir.BinaryOperationKind.DIVIDE, new Hir.Literal("0", Ty.INTEGER)
      ))
    }, null);
    final var root = new Hir.Program(new Hir.Expressions(new Hir.Expression[]{
      new Hir.Loop(tuple, Ty.VOID, null), new Hir.Return(new Hir.Literal("7", Ty.INTEGER))
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
      final var root = new Hir.Program(new Hir.Loop(body, Ty.VOID, null));
      final var thir = new HirToThirRaising(new MachineTarget(64)).raise(root);
      assertThrows(UnreachableCodeException.class, () -> ThirToMirLowering.lower(thir));
    }
  }
}
