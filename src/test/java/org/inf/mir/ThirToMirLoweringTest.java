package org.inf.mir;

import org.inf.Inf;
import org.inf.exceptions.UnreachableCodeException;
import org.inf.execution.interpreter.InterpreterCodeExecutor;
import org.inf.hir.Hir;
import org.inf.mir.model.MirNode;
import org.inf.thir.raising.HirToThirRaising;
import org.inf.ty.Ty;
import org.inf.ty.TyUnion;
import org.inf.ty.util.MachineTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ThirToMirLoweringTest {

  private Object run(String code) {
    return new InterpreterCodeExecutor().execute(Inf.codeToMir(code).initNode());
  }

  private List<Mir.Instruction> instructions(MirLoweringResult module) {
    return module.script().blocks().stream().flatMap(block -> block.instructions().stream()).toList();
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
      pair(b: bump(values), a: bump(values))
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
  void statementsAfterBreakOrContinueAreRejected() {
    for (final var transfer : List.of(new Hir.LoopBreak(null), new Hir.LoopContinue())) {
      final var body = new Hir.Expressions(new Hir.Expression[]{transfer, new Hir.Literal("1", Ty.INTEGER)}, Ty.INTEGER);
      final var root = new Hir.Program(new Hir.Loop(body, Ty.VOID, null));
      final var thir = new HirToThirRaising(new MachineTarget(64)).raise(root);
      assertThrows(UnreachableCodeException.class, () -> ThirToMirLowering.lower(thir));
    }
  }
}
