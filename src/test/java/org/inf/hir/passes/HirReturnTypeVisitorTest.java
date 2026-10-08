package org.inf.hir.passes;

import org.inf.hir.Hir;
import org.inf.ty.*;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HirReturnTypeVisitorTest {

  private Hir.Call call(Ty result, Hir.Expression... operands) {
    final var parameters = new Hir.Parameter[operands.length];
    final var arguments = new Hir.Argument[operands.length];
    for (var i = 0; i < operands.length; i++) {
      parameters[i] = new Hir.Parameter(new Hir.Lexeme("p" + i), new Hir.TyExpr(Ty.INTEGER), false, null);
      arguments[i] = new Hir.Argument(null, operands[i]);
    }
    return new Hir.Call(
      new Hir.FunctionSignature(parameters, false, new Hir.TyExpr(result), null),
      arguments,
      false,
      null
    );
  }

  @Test
  void callsKeepNominalTypeButExcludeLaterArgumentReturns() {
    final var call = call(
      Ty.BOOLEAN,
      new Hir.Return(new Hir.Literal("1", Ty.INTEGER)),
      new Hir.Return(new Hir.Literal("text", Ty.STRING))
    );
    final var ret = new Hir.Return(call);
    final var program = HirTyCommonVisitorPass.pass(new Hir.Program(ret));

    assertEquals(Ty.INTEGER, program.ty());
    assertEquals(Ty.DEADEND, ret.ty());
    assertEquals(Ty.DEADEND, call.ty());
    assertEquals(Ty.BOOLEAN, Tys.getCallableSignature(call.target()).returnTy());
  }

  @Test
  void nonReturningCalleeHasNoHypotheticalNormalResult() {
    final var call = call(Ty.DEADEND);
    final var sequence = HirTyCommonVisitorPass.pass(new Hir.Expressions(new Hir.Expression[]{call, new Hir.Return(new Hir.Literal("text", Ty.STRING))}));

    assertEquals(Ty.DEADEND, sequence.ty());
    assertEquals(Ty.DEADEND, call.ty());
    assertEquals(Ty.DEADEND, Tys.getCallableSignature(call.target()).returnTy());
  }

  @Test
  void callTargetTransferPreventsArgumentEvaluation() {
    final var signature = new Hir.FunctionSignature(new Hir.Parameter[0], false, new Hir.TyExpr(Ty.BOOLEAN), null);
    final var target = new Hir.Conditional(new Hir.Return(new Hir.Literal("1", Ty.INTEGER)), signature, signature, null);
    final var call = new Hir.Call(
      target,
      new Hir.Argument[] { new Hir.Argument(null, new Hir.Return(new Hir.Literal("text", Ty.STRING))) },
      false,
      null
    );
    final var ret = new Hir.Return(call);
    final var program = HirTyCommonVisitorPass.pass(new Hir.Program(ret));

    assertEquals(Ty.INTEGER, program.ty());
    assertEquals(Ty.DEADEND, ret.ty());
    assertEquals(Ty.DEADEND, target.ty());
    assertInstanceOf(TyFn.class, Tys.getCallableSignature(target));
    assertEquals(Ty.BOOLEAN, Tys.getCallableSignature(call.target()).returnTy());
    assertEquals(Ty.DEADEND, call.ty());
  }

  @Test
  void eagerBinaryUsesCompletionTypesAndStopsAtFirstTransfer() {
    final var left = call(Ty.INTEGER, new Hir.Return(new Hir.Literal("1", Ty.INTEGER)));
    final var right = call(Ty.INTEGER, new Hir.Return(new Hir.Literal("text", Ty.STRING)));
    final var binary = new Hir.BinaryOperation(left, Hir.BinaryOperationKind.ADD, right, null);
    final var ret = new Hir.Return(binary);
    final var program = HirTyCommonVisitorPass.pass(new Hir.Program(ret));

    assertEquals(Ty.INTEGER, program.ty());
    assertEquals(Ty.DEADEND, ret.ty());
    assertEquals(Ty.DEADEND, binary.ty());
  }

  @Test
  void shortCircuitLeftTransferIsNotMaskedByBooleanResult() {
    final var binary = new Hir.BinaryOperation(
      new Hir.Return(new Hir.Literal("1", Ty.INTEGER)),
      Hir.BinaryOperationKind.AND,
      new Hir.Return(new Hir.Literal("text", Ty.STRING))
    );

    final var ret = new Hir.Return(binary);
    final var program = new Hir.Program(ret);

    HirTyCommonVisitorPass.pass(program);

    assertAll(
      () -> assertEquals(Ty.INTEGER, program.ty(), "Program should be int"),
      () -> assertEquals(Ty.DEADEND, ret.ty(), "Return should be dead-end"),
      () -> assertEquals(Ty.DEADEND, binary.ty(), "binary ty should be dead-end")
    );
  }

  @Test
  void arrayElementsAreRuntimeExpressionsAndLengthIsEvaluatedLast() {
    final var array = new Hir.Array(
      new Hir.Expression[]{new Hir.Return(new Hir.Literal("1", Ty.INTEGER)), new Hir.Return(new Hir.Literal("text", Ty.STRING))},
      new Hir.TyExpr(Ty.INTEGER),
      new Hir.Return(new Hir.Literal("4", Ty.INTEGER)),
      null, null
    );
    final var ret = new Hir.Return(array);
    final var program = new Hir.Program(ret);

    HirTyCommonVisitorPass.pass(program);

    assertAll(
      () -> assertEquals(Ty.INTEGER, program.ty()),
      () -> assertEquals(Ty.DEADEND, ret.ty()),
      () -> assertEquals(Ty.DEADEND, array.ty()),
      () -> assertEquals(Ty.INTEGER, assertInstanceOf(TyValueArray.class, array.arrayTy()).elementType())
    );
  }

  @Test
  void returningArrayLengthRetainsNominalArrayLayout() {
    final var array = new Hir.Array(
      new Hir.Expression[]{new Hir.Literal("1", Ty.INTEGER)},
      new Hir.TyExpr(Ty.INTEGER), new Hir.Return(new Hir.Literal("text", Ty.STRING)),
      null, null
    );
    final var ret = new Hir.Return(array);
    final var program = HirTyCommonVisitorPass.pass(new Hir.Program(ret));

    assertEquals(Ty.STRING, program.ty());
    assertEquals(Ty.DEADEND, ret.ty());
    assertEquals(Ty.DEADEND, array.ty());
    assertInstanceOf(TyValueArray.class, array.arrayTy());
  }

  @Test
  void given__negation_of_return__when__typed__then__completion_remains_deadend() {
    final var not = new Hir.Not(new Hir.Return(new Hir.Literal("1", Ty.INTEGER)), null);
    final var ret = new Hir.Return(not);
    final var program = HirTyCommonVisitorPass.pass(new Hir.Program(ret));

    assertEquals(Ty.INTEGER, program.ty());
    assertEquals(Ty.DEADEND, ret.ty());
    assertEquals(Ty.DEADEND, not.ty());
  }

  @Test
  void loopsWithoutReachableBreaksDoNotAddVoidFallthrough() {
    final var loop = new Hir.Loop(new Hir.Expressions(new Hir.Expression[]{
      new Hir.Return(new Hir.Literal("1", Ty.INTEGER)), new Hir.LoopBreak(null)
    }));
    final var program = new Hir.Program(loop);
    HirTyCommonVisitorPass.pass(program);

    assertEquals(Ty.INTEGER, program.ty());
    assertEquals(Ty.DEADEND, loop.ty());
  }

  @Test
  void breakOperandReturnsDoNotCountAsLoopExits() {
    final var loop = new Hir.Loop(
      new Hir.LoopBreak(new Hir.Return(new Hir.Literal("1", Ty.INTEGER)))
    );
    final var program = new Hir.Program(loop);

    HirTyCommonVisitorPass.pass(program);

    assertAll(
      () -> assertEquals(Ty.INTEGER, program.ty(), "Program should be int"),
      () -> assertEquals(Ty.DEADEND, loop.ty(), "Loop ty should be dead-end")
    );
  }

  @Test
  void bareBreakAndValueBreakBothContributeToLoopResult() {
    final var loop = new Hir.Loop(
      new Hir.Conditional(
        new Hir.Literal("true", Ty.BOOLEAN),
        new Hir.LoopBreak(new Hir.Literal("1", Ty.INTEGER)),
        new Hir.LoopBreak(null),
        null
      )
    );

    HirTyCommonVisitorPass.pass(loop);

    assertEquals(Tys.union(Ty.INTEGER, Ty.VOID), loop.ty());
  }

  @Test
  void nestedLoopBreakDoesNotMakeOuterLoopContinue() {
    final var inner = new Hir.Loop(new Hir.LoopBreak(null));
    final var outer = new Hir.Loop(inner);
    final var program = new Hir.Program(outer);

    HirTyCommonVisitorPass.pass(program);

    assertAll(
      () -> assertEquals(Ty.VOID, outer.ty()),
      () -> assertEquals(Ty.VOID, inner.ty()),
      () -> assertEquals(Ty.VOID, program.ty())
    );
  }

  @Test
  void breakStopsUnreachableReturnsButPermitsExecutionAfterLoop() {
    final var loop = new Hir.Loop(new Hir.Expressions(new Hir.Expression[]{
      new Hir.LoopBreak(null),
      new Hir.Return(new Hir.Literal("text", Ty.STRING))
    }));
    final var body = new Hir.Expressions(new Hir.Expression[]{
      loop,
      new Hir.Return(new Hir.Literal("1", Ty.INTEGER))
    });
    final var program = new Hir.Program(body);

    HirTyCommonVisitorPass.pass(program);

    assertEquals(Ty.INTEGER, program.ty());
    assertEquals(Ty.DEADEND, body.ty());
    assertEquals(Ty.VOID, loop.ty());
  }

  @Test
  void fieldInitializersPreserveNominalLayout() {
    final var layout = new TyStruct(new TyField[]{new TyField("x", Ty.INTEGER)});
    final var field = new Hir.Assignment(
      new Hir.Dec(new Hir.Lexeme("x"), Hir.MutabilityKind.MUTABLE, new Hir.TyExpr(Ty.INTEGER)),
      new Hir.Return(new Hir.Literal("1", Ty.INTEGER))
    );
    final var block = new Hir.NewByBlock(new Hir.TyExpr(layout), null, new Hir.Assignment[]{field}, null);
    final var ret = new Hir.Return(block);
    final var program = HirTyCommonVisitorPass.pass(new Hir.Program(ret));

    assertEquals(Ty.INTEGER, program.ty());
    assertEquals(Ty.DEADEND, ret.ty());
    assertEquals(Ty.DEADEND, block.ty());
    assertEquals(layout, Tys.getConstructionTargetTy(block.target()));
  }

  @Test
  void constructorsPreserveNominalLayout() {
    final var layout = new TyStruct(new TyField[]{new TyField("x", Ty.INTEGER)});
    final var ctor = new Hir.NewByCtor(new Hir.TyExpr(layout), null, new Hir.Return(new Hir.Literal("1", Ty.INTEGER)), null);
    final var ret = new Hir.Return(ctor);
    final var program = HirTyCommonVisitorPass.pass(new Hir.Program(ret));

    assertEquals(Ty.INTEGER, program.ty());
    assertEquals(Ty.DEADEND, ret.ty());
    assertEquals(Ty.DEADEND, ctor.ty());
    assertEquals(layout, Tys.getConstructionTargetTy(ctor.target()));
  }

  @Test
  void memberAccessUsesNominalReceiverTypeAfterTransfer() {
    final var layout = new TyStruct(new TyField[]{new TyField("x", Ty.INTEGER)});
    final var receiver = call(layout, new Hir.Return(new Hir.Literal("text", Ty.STRING)));
    final var path = new Hir.Path(new Hir.Expression[]{receiver, new Hir.Lexeme("x")}, null, null);
    final var ret = new Hir.Return(path);
    final var program = HirTyCommonVisitorPass.pass(new Hir.Program(ret));

    assertEquals(Ty.STRING, program.ty());
    assertEquals(Ty.DEADEND, ret.ty());
    assertEquals(Ty.DEADEND, path.ty());
    assertEquals(Ty.INTEGER, path.memberTy());
  }

  @Test
  void arrayAccessPreservesElementTypeThroughNonContinuingReceiver() {
    final var arrayTy = new TyValueArray(Ty.INTEGER, 1);
    final var receiver = call(arrayTy, new Hir.Return(new Hir.Literal("text", Ty.STRING)));
    final var access = new Hir.ArrayAccess(receiver, new Hir.Return(new Hir.Literal("1", Ty.INTEGER)), null, null);
    final var ret = new Hir.Return(access);
    final var program = HirTyCommonVisitorPass.pass(new Hir.Program(ret));

    assertEquals(Ty.STRING, program.ty());
    assertEquals(Ty.DEADEND, ret.ty());
    assertEquals(Ty.DEADEND, access.ty());
    assertEquals(Ty.INTEGER, access.indexedTy());
  }

  @Test
  void given__unreachable_tail__when__sequence_and_block_are_typed__then__completion_and_returns_ignore_the_tail() {
    final var sequence = new Hir.Expressions(new Hir.Expression[]{
      new Hir.Return(new Hir.Literal("1", Ty.INTEGER)),
      new Hir.Literal("text", Ty.STRING)
    });
    final var block = new Hir.Block(sequence, null);
    final var program = new Hir.Program(block);

    HirTyCommonVisitorPass.pass(program);

    assertEquals(Ty.INTEGER, program.ty());
    assertEquals(Ty.DEADEND, block.ty());
    assertEquals(Ty.DEADEND, sequence.ty());
    assertEquals(Ty.STRING, sequence.children()[1].ty());
  }

  @Test
  void nestedFunctionBodiesDoNotExecuteWhenPassedAsArguments() {
    final var nested = new Hir.Function(
      new Hir.FunctionSignature(new Hir.Parameter[0], false, new Hir.TyExpr(Ty.INFER), null),
      new Hir.Return(new Hir.Literal("text", Ty.STRING))
    );
    final var call = call(Ty.BOOLEAN, nested);
    final var ret = new Hir.Return(call);
    final var program = HirTyCommonVisitorPass.pass(new Hir.Program(ret));

    assertEquals(Ty.BOOLEAN, program.ty());
    assertEquals(Ty.DEADEND, ret.ty());
    assertEquals(Ty.STRING, nested.ty().returnTy());
  }

  @Test
  void given__returning_range_bounds__when__typed__then__payloads_are_not_used_as_bounds() {
    final var rangeTy = new Hir.Range(
      new Hir.Return(new Hir.Literal("1", Ty.INTEGER)),
      new Hir.Return(new Hir.Literal("text", Ty.STRING))
    );
    final var returnTy = new Hir.Return(rangeTy);
    final var program = new Hir.Program(returnTy);

    HirTyCommonVisitorPass.pass(program);

    assertAll(
      () -> assertEquals(Ty.INTEGER, program.ty()),
      () -> assertEquals(Ty.DEADEND, rangeTy.ty()),
      () -> assertEquals(Ty.DEADEND, returnTy.ty(), "ret should be dead-end"),
      () -> assertEquals(new TyValueArray(Ty.DEADEND, null), rangeTy.rangeTy()),
      () -> assertEquals(rangeTy.rangeTy(), Tys.getIndexingAccessorTy(rangeTy))
    );
  }

  @Test
  void rangePreservesTypeForDeadEndWithCompatibleBounds() {
    final var rangeTy = new Hir.Range(new Hir.Literal("1", Ty.INTEGER), new Hir.Literal("1", Ty.INTEGER));
    final var returnTy = new Hir.Return(rangeTy);
    final var program = new Hir.Program(returnTy);

    HirTyCommonVisitorPass.pass(program);

    assertAll(
      () -> assertEquals(new TyValueArray(Ty.INTEGER, null), program.ty()),
      () -> assertEquals(new TyValueArray(Ty.INTEGER, null), rangeTy.ty())
    );
  }

  @Test
  void emptyBodyCompletesWithVoid() {
    final var expressions = new Hir.Expressions(new Hir.Expression[0]);

    HirTyCommonVisitorPass.pass(expressions);

    assertEquals(Ty.VOID, expressions.ty());
  }
}
