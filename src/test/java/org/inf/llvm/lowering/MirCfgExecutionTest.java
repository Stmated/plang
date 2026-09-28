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
