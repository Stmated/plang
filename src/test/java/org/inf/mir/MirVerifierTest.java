package org.inf.mir;

import org.inf.mir.model.MirBinaryOperationKind;
import org.inf.mir.model.MirFnParameter;
import org.inf.mir.model.MirFnSignature;
import org.inf.mir.model.MirFunction;
import org.inf.mir.model.MirNode;
import org.inf.ty.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MirVerifierTest {

  @Test
  void given__different_tuple_layouts__when__pointer_converted__then__rejected() {
    final var from = new TyPointer<>(new TyStruct(new TyField[]{new TyField(null, Ty.INTEGER)}));
    final var to = new TyPointer<>(new TyStruct(new TyField[]{new TyField(null, Ty.LONG)}));
    final var function = function(to, from);
    final var parameter = function.newValue(from);
    final var converted = function.newValue(to);
    function.entry().append(new Mir.Parameter(parameter, 0));
    function.entry().append(new Mir.Convert(converted, parameter));
    function.entry().terminate(new Mir.Return(converted));
    reject(function, "Invalid explicit conversion");
  }

  @Test
  void given__same_tuple_layout_with_explicit_width_metadata__when__pointer_converted__then__accepted() {
    final var from = new TyPointer<>(new TyStruct(new TyField[]{new TyField(null, Ty.INTEGER)}));
    final var explicit = Ty.INTEGER.toBuilder().width(new BitWidth(32, true)).build();
    final var to = new TyPointer<>(new TyStruct(new TyField[]{new TyField(null, explicit)}));
    final var function = function(to, from);
    final var parameter = function.newValue(from);
    final var converted = function.newValue(to);
    function.entry().append(new Mir.Parameter(parameter, 0));
    function.entry().append(new Mir.Convert(converted, parameter));
    function.entry().terminate(new Mir.Return(converted));
    assertDoesNotThrow(() -> verify(function));
  }

  private static final Mir.Constant ONE = new Mir.Constant("1", Ty.INTEGER);
  private static final Mir.Constant TRUE = new Mir.Constant("true", Ty.BOOLEAN);

  private static MirFunction function(Ty result, Ty... parameters) {
    final var params = new MirFnParameter[parameters.length];
    for (var i = 0; i < parameters.length; i++) {
      params[i] = new MirFnParameter("arg" + i, parameters[i]);
    }
    return new MirFunction("test", new MirFnSignature(params, false, result), false);
  }

  private static MirLoweringResult module(MirFunction function, MirFunction... others) {
    final var functions = new java.util.ArrayList<MirFunction>();
    functions.add(function);
    functions.addAll(List.of(others));
    return new MirLoweringResult(function, functions);
  }

  private static void verify(MirFunction function, MirFunction... others) {
    MirVerifier.verify(module(function, others));
  }

  private static void reject(MirFunction function, String diagnostic, MirFunction... others) {
    final var error = assertThrows(IllegalArgumentException.class, () -> verify(function, others));
    assertTrue(error.getMessage().contains(diagnostic), error.getMessage());
  }

  private static void done(MirNode block) {
    block.terminate(new Mir.Return(Mir.Unit.INSTANCE));
  }

  private static Mir.Value add(MirFunction function, MirNode block) {
    final var result = function.newValue(Ty.INTEGER);
    block.append(new Mir.Binary(result, ONE, MirBinaryOperationKind.ADD, ONE));
    return result;
  }

  @Test
  void acceptsExplicitValuesLocalsAndReturn() {
    final var function = function(Ty.INTEGER, Ty.INTEGER);
    final var parameter = function.newValue(Ty.INTEGER);
    final var local = function.newLocal("parameter", Ty.INTEGER);
    function.entry().append(new Mir.Parameter(parameter, 0));
    function.entry().append(new Mir.Store(local, parameter));
    final var loaded = function.newValue(Ty.INTEGER);
    function.entry().append(new Mir.Load(loaded, local));
    function.entry().terminate(new Mir.Return(loaded));
    assertDoesNotThrow(() -> verify(function));
  }

  @Test
  void everyBlockMustTerminateIncludingUnreachableBlocks() {
    final var function = function(Ty.VOID);
    reject(function, "Unterminated");
    done(function.entry());
    function.newBlock("dead");
    reject(function, "Unterminated");
  }

  @Test
  void blocksRejectInstructionsAndSecondTerminatorsAfterTermination() {
    final var function = function(Ty.VOID);
    done(function.entry());
    assertThrows(IllegalStateException.class, () -> function.entry().append(
      new Mir.Binary(function.newValue(Ty.INTEGER), ONE, MirBinaryOperationKind.ADD, ONE)));
    assertThrows(IllegalStateException.class, () -> done(function.entry()));
  }

  @Test
  void rejectsForeignAndUnownedControlFlowEdges() {
    final var first = function(Ty.VOID);
    final var second = function(Ty.VOID);
    first.entry().terminate(new Mir.Jump(second.entry()));
    done(second.entry());
    reject(first, "CFG edge", second);

    final var unowned = function(Ty.VOID);
    unowned.entry().terminate(new Mir.Jump(new MirNode("unowned")));
    reject(unowned, "CFG edge");
  }

  @Test
  void rejectsIncomingEdgesToFunctionEntry() {
    final var function = function(Ty.VOID);
    function.entry().terminate(new Mir.Jump(function.entry()));
    reject(function, "entry has an incoming");
  }

  @Test
  void valuesWithEqualIdsInDifferentFunctionsAreNotInterchangeable() {
    final var first = function(Ty.INTEGER);
    final var firstValue = add(first, first.entry());
    first.entry().terminate(new Mir.Return(firstValue));
    final var second = function(Ty.INTEGER);
    final var secondValue = add(second, second.entry());
    assertEquals(firstValue, secondValue);
    assertNotSame(firstValue, secondValue);
    second.entry().terminate(new Mir.Return(firstValue));
    reject(first, "cross-function value", second);
  }

  @Test
  void rejectsEqualButNotIdenticalValueAndLocalUses() {
    final var function = function(Ty.INTEGER);
    final var result = add(function, function.entry());
    function.entry().terminate(new Mir.Return(new Mir.Value(result.id(), result.ty())));
    reject(function, "Undefined");

    final var localFunction = function(Ty.VOID);
    final var local = localFunction.newLocal("x", Ty.INTEGER);
    localFunction.entry().append(new Mir.Store(new Mir.Local(local.id(), local.name(), local.ty()), ONE));
    done(localFunction.entry());
    reject(localFunction, "Unowned");
  }

  @Test
  void rejectsCrossFunctionLocalEvenWhenLocalIdsMatch() {
    final var first = function(Ty.VOID);
    final var local = first.newLocal("x", Ty.INTEGER);
    done(first.entry());
    final var second = function(Ty.VOID);
    assertEquals(local, second.newLocal("x", Ty.INTEGER));
    second.entry().append(new Mir.Store(local, ONE));
    done(second.entry());
    reject(first, "cross-function local", second);
  }

  @Test
  void rejectsDuplicateResultObjectsAndDuplicateIds() {
    final var function = function(Ty.INTEGER);
    final var value = add(function, function.entry());
    function.entry().append(new Mir.Binary(value, ONE, MirBinaryOperationKind.ADD, ONE));
    function.entry().terminate(new Mir.Return(value));
    reject(function, "Duplicate value");

    final var ids = function(Ty.INTEGER);
    final var original = add(ids, ids.entry());
    ids.entry().append(new Mir.Binary(new Mir.Value(original.id(), Ty.LONG),
      new Mir.Constant("1", Ty.LONG), MirBinaryOperationKind.ADD, new Mir.Constant("2", Ty.LONG)));
    ids.entry().terminate(new Mir.Return(original));
    reject(ids, "Duplicate value");
  }

  @Test
  void rejectsReusedInstructionAndTerminatorObjects() {
    final var function = function(Ty.VOID);
    final var local = function.newLocal("x", Ty.INTEGER);
    final var store = new Mir.Store(local, ONE);
    function.entry().append(store);
    function.entry().append(store);
    done(function.entry());
    reject(function, "Instruction belongs");

    final var terminalFunction = function(Ty.VOID);
    final var terminal = new Mir.Unreachable();
    terminalFunction.entry().terminate(terminal);
    terminalFunction.newBlock("dead").terminate(terminal);
    reject(terminalFunction, "Terminator belongs");
  }

  @Test
  void rejectsValuesUsedBeforeTheirDefinition() {
    final var function = function(Ty.INTEGER);
    final var later = function.newValue(Ty.INTEGER);
    final var earlier = function.newValue(Ty.INTEGER);
    function.entry().append(new Mir.Binary(earlier, later, MirBinaryOperationKind.ADD, ONE));
    function.entry().append(new Mir.Binary(later, ONE, MirBinaryOperationKind.ADD, ONE));
    function.entry().terminate(new Mir.Return(earlier));
    reject(function, "does not dominate");
  }

  @Test
  void rejectsSelfReferentialInstructionResult() {
    final var function = function(Ty.INTEGER);
    final var value = function.newValue(Ty.INTEGER);
    function.entry().append(new Mir.Binary(value, value, MirBinaryOperationKind.ADD, ONE));
    function.entry().terminate(new Mir.Return(value));
    reject(function, "does not dominate");
  }

  @Test
  void branchSpecificValuesDoNotDominateAMerge() {
    final var function = function(Ty.INTEGER);
    final var left = function.newBlock("left");
    final var right = function.newBlock("right");
    final var merge = function.newBlock("merge");
    function.entry().terminate(new Mir.Branch(TRUE, left, right));
    final var value = add(function, left);
    left.terminate(new Mir.Jump(merge));
    right.terminate(new Mir.Jump(merge));
    merge.terminate(new Mir.Return(value));
    reject(function, "does not dominate");
  }

  @Test
  void entryValuesDominateBothArmsAndMerge() {
    final var function = function(Ty.INTEGER);
    final var value = add(function, function.entry());
    final var left = function.newBlock("left");
    final var right = function.newBlock("right");
    final var merge = function.newBlock("merge");
    function.entry().terminate(new Mir.Branch(TRUE, left, right));
    left.terminate(new Mir.Jump(merge));
    right.terminate(new Mir.Jump(merge));
    merge.terminate(new Mir.Return(value));
    assertDoesNotThrow(() -> verify(function));
  }

  @Test
  void rejectsUninitializedLoadsEvenIfAStoreFollows() {
    final var function = function(Ty.INTEGER);
    final var local = function.newLocal("result", Ty.INTEGER);
    final var value = function.newValue(Ty.INTEGER);
    function.entry().append(new Mir.Load(value, local));
    function.entry().append(new Mir.Store(local, ONE));
    function.entry().terminate(new Mir.Return(value));
    reject(function, "uninitialized local 'result'");
  }

  private static MirFunction diamond(boolean initializeBoth) {
    final var function = function(Ty.INTEGER);
    final var local = function.newLocal("result", Ty.INTEGER);
    final var left = function.newBlock("left");
    final var right = function.newBlock("right");
    final var merge = function.newBlock("merge");
    function.entry().terminate(new Mir.Branch(TRUE, left, right));
    left.append(new Mir.Store(local, ONE));
    left.terminate(new Mir.Jump(merge));
    if (initializeBoth) {
      right.append(new Mir.Store(local, ONE));
    }
    right.terminate(new Mir.Jump(merge));
    final var result = function.newValue(Ty.INTEGER);
    merge.append(new Mir.Load(result, local));
    merge.terminate(new Mir.Return(result));
    return function;
  }

  @Test
  void mergeRequiresInitializationOnEveryIncomingPath() {
    reject(diamond(false), "uninitialized");
    assertDoesNotThrow(() -> verify(diamond(true)));
  }

  private static MirFunction loop(boolean initializeEntry) {
    final var function = function(Ty.INTEGER);
    final var local = function.newLocal("loop-carried", Ty.INTEGER);
    final var header = function.newBlock("header");
    final var body = function.newBlock("body");
    final var exit = function.newBlock("exit");
    if (initializeEntry) {
      function.entry().append(new Mir.Store(local, ONE));
    }
    function.entry().terminate(new Mir.Jump(header));
    final var value = function.newValue(Ty.INTEGER);
    header.append(new Mir.Load(value, local));
    header.terminate(new Mir.Branch(TRUE, body, exit));
    body.append(new Mir.Store(local, ONE));
    body.terminate(new Mir.Jump(header));
    exit.terminate(new Mir.Return(value));
    return function;
  }

  @Test
  void loopBackEdgeDoesNotInitializeTheFirstIteration() {
    reject(loop(false), "uninitialized");
    assertDoesNotThrow(() -> verify(loop(true)));
  }

  @Test
  void loopAnalysisConvergesAcrossSeveralBlocks() {
    final var function = function(Ty.INTEGER);
    final var local = function.newLocal("x", Ty.INTEGER);
    final var header = function.newBlock("header");
    final var exit = function.newBlock("exit");
    final var latch = function.newBlock("latch");
    final var body = function.newBlock("body");
    function.entry().terminate(new Mir.Jump(header));
    header.terminate(new Mir.Branch(TRUE, body, exit));
    body.terminate(new Mir.Jump(latch));
    latch.append(new Mir.Store(local, ONE));
    latch.terminate(new Mir.Jump(header));
    final var value = function.newValue(Ty.INTEGER);
    exit.append(new Mir.Load(value, local));
    exit.terminate(new Mir.Return(value));
    reject(function, "uninitialized");
  }

  @Test
  void unreachablePredecessorsDoNotEraseReachableInitialization() {
    final var function = function(Ty.INTEGER);
    final var local = function.newLocal("x", Ty.INTEGER);
    final var exit = function.newBlock("exit");
    final var dead = function.newBlock("dead");
    function.entry().append(new Mir.Store(local, ONE));
    function.entry().terminate(new Mir.Jump(exit));
    dead.terminate(new Mir.Jump(exit));
    final var value = function.newValue(Ty.INTEGER);
    exit.append(new Mir.Load(value, local));
    exit.terminate(new Mir.Return(value));
    assertDoesNotThrow(() -> verify(function));
  }

  @Test
  void isolatedUnreachableBlocksStillCheckLocalInstructionOrder() {
    final var function = function(Ty.VOID);
    done(function.entry());
    final var dead = function.newBlock("dead");
    final var local = function.newLocal("dead-local", Ty.INTEGER);
    dead.append(new Mir.Store(local, ONE));
    dead.append(new Mir.Load(function.newValue(Ty.INTEGER), local));
    dead.terminate(new Mir.Unreachable());
    function.newBlock("unused-exit").terminate(new Mir.Unreachable());
    assertDoesNotThrow(() -> verify(function));

    final var invalid = function(Ty.VOID);
    done(invalid.entry());
    final var invalidDead = invalid.newBlock("dead");
    invalidDead.append(new Mir.Load(invalid.newValue(Ty.INTEGER), invalid.newLocal("x", Ty.INTEGER)));
    invalidDead.terminate(new Mir.Unreachable());
    reject(invalid, "uninitialized");
  }

  @Test
  void rejectsUnresolvedTypesRecursivelyAndVoidValues() {
    for (final var type : List.of(Ty.UNKNOWN, Ty.INFER, Ty.INVALID, Ty.DEADEND,
      new TyIdentifier("Unresolved"), new TyPointer<>(Ty.UNKNOWN), Ty.VOID)) {
      final var function = function(Ty.VOID);
      function.newLocal("bad", type);
      done(function.entry());
      assertThrows(IllegalArgumentException.class, () -> verify(function), type.toString());
    }
  }

  @Test
  void rejectsMissingResultsAndMismatchedBinaryTypes() {
    final var missing = function(Ty.VOID);
    missing.entry().append(new Mir.Binary(null, ONE, MirBinaryOperationKind.ADD, ONE));
    done(missing.entry());
    reject(missing, "missing a result");

    final var mismatch = function(Ty.VOID);
    mismatch.entry().append(new Mir.Binary(mismatch.newValue(Ty.BOOLEAN), ONE, MirBinaryOperationKind.ADD, ONE));
    done(mismatch.entry());
    reject(mismatch, "Binary result type mismatch");

    final var invalidLogical = function(Ty.VOID);
    invalidLogical.entry().append(new Mir.Binary(invalidLogical.newValue(Ty.BOOLEAN), ONE, MirBinaryOperationKind.AND, ONE));
    done(invalidLogical.entry());
    reject(invalidLogical, "Logical operations must be lowered to branches");
  }

  @Test
  void eagerLogicalInstructionsAreNotPermittedEvenWithBooleanOperands() {
    for (final var kind : List.of(MirBinaryOperationKind.AND, MirBinaryOperationKind.OR)) {
      final var function = function(Ty.BOOLEAN);
      final var result = function.newValue(Ty.BOOLEAN);
      function.entry().append(new Mir.Binary(result, TRUE, kind, TRUE));
      function.entry().terminate(new Mir.Return(result));
      reject(function, "Logical operations must be lowered to branches");
    }
  }

  @Test
  void rejectsNonBooleanBranchesAndMismatchedReturns() {
    final var function = function(Ty.VOID);
    final var exit = function.newBlock("exit");
    function.entry().terminate(new Mir.Branch(ONE, exit, exit));
    done(exit);
    reject(function, "Branch predicate");

    final var wrongReturn = function(Ty.LONG);
    wrongReturn.entry().terminate(new Mir.Return(ONE));
    reject(wrongReturn, "Return type mismatch");
  }

  @Test
  void loadsAndStoresRequireExactTypesNotImplicitNumericConversions() {
    final var wrongStore = function(Ty.VOID);
    wrongStore.entry().append(new Mir.Store(wrongStore.newLocal("wide", Ty.LONG), ONE));
    done(wrongStore.entry());
    reject(wrongStore, "Store type mismatch");

    final var wrongLoad = function(Ty.VOID);
    final var local = wrongLoad.newLocal("wide", Ty.LONG);
    wrongLoad.entry().append(new Mir.Store(local, new Mir.Constant("1", Ty.LONG)));
    wrongLoad.entry().append(new Mir.Load(wrongLoad.newValue(Ty.INTEGER), local));
    done(wrongLoad.entry());
    reject(wrongLoad, "Load type mismatch");
  }

  @Test
  void parameterIndicesTypesAndPlacementAreChecked() {
    final var badIndex = function(Ty.VOID, Ty.INTEGER);
    badIndex.entry().append(new Mir.Parameter(badIndex.newValue(Ty.INTEGER), 1));
    done(badIndex.entry());
    reject(badIndex, "Parameter index");

    final var duplicate = function(Ty.VOID, Ty.INTEGER);
    duplicate.entry().append(new Mir.Parameter(duplicate.newValue(Ty.INTEGER), 0));
    duplicate.entry().append(new Mir.Parameter(duplicate.newValue(Ty.INTEGER), 0));
    done(duplicate.entry());
    reject(duplicate, "Parameter must be defined once");

    final var misplaced = function(Ty.VOID, Ty.INTEGER);
    final var next = misplaced.newBlock("next");
    misplaced.entry().terminate(new Mir.Jump(next));
    next.append(new Mir.Parameter(misplaced.newValue(Ty.INTEGER), 0));
    done(next);
    reject(misplaced, "entry block");
  }

  @Test
  void functionReferencesMustBeOwnedByTheModule() {
    final var function = function(Ty.VOID);
    final var external = new MirFunction("external",
      new MirFnSignature(new MirFnParameter[0], false, Ty.VOID), true);
    function.entry().append(new Mir.Call(null, new Mir.FunctionRef(external), external.signature(), List.of()));
    done(function.entry());
    reject(function, "outside module");
    assertDoesNotThrow(() -> verify(function, external));
  }

  @Test
  void callSignaturesAndArgumentTypesCannotBeInventedAtTheCallSite() {
    final var target = new MirFunction("target",
      new MirFnSignature(new MirFnParameter[]{new MirFnParameter("x", Ty.INTEGER)}, false, Ty.INTEGER), true);
    final var wrongSignature = function(Ty.VOID);
    wrongSignature.entry().append(new Mir.Call(null, new Mir.FunctionRef(target),
      new MirFnSignature(new MirFnParameter[0], false, Ty.VOID), List.of()));
    done(wrongSignature.entry());
    reject(wrongSignature, "Call target signature", target);

    final var wrongArgument = function(Ty.VOID);
    wrongArgument.entry().append(new Mir.Call(wrongArgument.newValue(Ty.INTEGER),
      new Mir.FunctionRef(target), target.signature(), List.of(TRUE)));
    done(wrongArgument.entry());
    reject(wrongArgument, "Call argument 0", target);

    final var missingArgument = function(Ty.VOID);
    missingArgument.entry().append(new Mir.Call(missingArgument.newValue(Ty.INTEGER),
      new Mir.FunctionRef(target), target.signature(), List.of()));
    done(missingArgument.entry());
    reject(missingArgument, "argument count", target);
  }

  @Test
  void voidCallsCannotHaveResultsAndNonVoidCallsRequireThem() {
    final var voidTarget = new MirFunction("void",
      new MirFnSignature(new MirFnParameter[0], false, Ty.VOID), true);
    final var valueTarget = new MirFunction("value",
      new MirFnSignature(new MirFnParameter[0], false, Ty.INTEGER), true);
    final var voidCaller = function(Ty.VOID);
    voidCaller.entry().append(new Mir.Call(voidCaller.newValue(Ty.INTEGER),
      new Mir.FunctionRef(voidTarget), voidTarget.signature(), List.of()));
    done(voidCaller.entry());
    reject(voidCaller, "Void call", voidTarget);
    final var valueCaller = function(Ty.VOID);
    valueCaller.entry().append(new Mir.Call(null, new Mir.FunctionRef(valueTarget), valueTarget.signature(), List.of()));
    done(valueCaller.entry());
    reject(valueCaller, "Non-void call", valueTarget);
  }

  @Test
  void explicitConversionsAreCheckedRatherThanInferred() {
    final var function = function(Ty.LONG);
    final var converted = function.newValue(Ty.LONG);
    function.entry().append(new Mir.Convert(converted, ONE));
    function.entry().terminate(new Mir.Return(converted));
    assertDoesNotThrow(() -> verify(function));

    final var invalid = function(Ty.STRING);
    final var string = invalid.newValue(Ty.STRING);
    invalid.entry().append(new Mir.Convert(string, ONE));
    invalid.entry().terminate(new Mir.Return(string));
    reject(invalid, "Invalid explicit conversion");
  }

  @Test
  void numericConversionsFollowBackendSupportedPairs() {
    final Ty[][] supported = {
      {Ty.BOOLEAN, Ty.INTEGER}, {Ty.BOOLEAN, Ty.FLOAT}, {Ty.INTEGER, Ty.BOOLEAN},
      {Ty.INTEGER, Ty.DOUBLE}, {Ty.FLOAT, Ty.INTEGER}, {Ty.FLOAT, Ty.DOUBLE}
    };
    for (final var pair : supported) {
      final var function = function(pair[1], pair[0]);
      final var source = function.newValue(pair[0]);
      final var converted = function.newValue(pair[1]);
      function.entry().append(new Mir.Parameter(source, 0));
      function.entry().append(new Mir.Convert(converted, source));
      function.entry().terminate(new Mir.Return(converted));
      assertDoesNotThrow(() -> verify(function));
    }
  }

  @Test
  void unsupportedNumericAndPointerIntegerConversionsAreRejected() {
    final Ty[][] unsupported = {
      {Ty.FLOAT, Ty.BOOLEAN}, {Ty.DECIMAL, Ty.INTEGER}, {Ty.INTEGER, Ty.DECIMAL},
      {new TyPointer<>(Ty.CHAR), Ty.LONG}, {Ty.LONG, new TyPointer<>(Ty.CHAR)}
    };
    for (final var pair : unsupported) {
      final var function = function(pair[1], pair[0]);
      final var source = function.newValue(pair[0]);
      final var converted = function.newValue(pair[1]);
      function.entry().append(new Mir.Parameter(source, 0));
      function.entry().append(new Mir.Convert(converted, source));
      function.entry().terminate(new Mir.Return(converted));
      reject(function, "Invalid explicit conversion");
    }
  }

  @Test
  void stringsCanBeExplicitlyConvertedToCharacterReferencesForExternalCalls() {
    final var characterReference = new TyPointer<>(Ty.CHAR);
    final var external = new MirFunction("external",
      new MirFnSignature(new MirFnParameter[]{new MirFnParameter("text", characterReference)}, false, Ty.VOID), true);
    final var function = function(Ty.VOID);
    final var converted = function.newValue(characterReference);
    function.entry().append(new Mir.Convert(converted, new Mir.Constant("file", Ty.STRING)));
    function.entry().append(new Mir.Call(null, new Mir.FunctionRef(external), external.signature(), List.of(converted)));
    done(function.entry());
    assertDoesNotThrow(() -> verify(function, external));
  }

  @Test
  void characterReferencesCanBeExplicitlyConvertedBackToStrings() {
    final var characterReference = new TyPointer<>(Ty.CHAR);
    final var function = function(Ty.STRING, characterReference);
    final var parameter = function.newValue(characterReference);
    final var converted = function.newValue(Ty.STRING);
    function.entry().append(new Mir.Parameter(parameter, 0));
    function.entry().append(new Mir.Convert(converted, parameter));
    function.entry().terminate(new Mir.Return(converted));
    assertDoesNotThrow(() -> verify(function));
  }

  @Test
  void stringConversionsRejectOtherPointeesAndAddressSpacesInBothDirections() {
    for (final var pointer : List.of(new TyPointer<>(Ty.INTEGER), new TyPointer<>(new TyPointer<>(Ty.CHAR)),
      new TyPointer<>(Ty.CHAR, TyPointerAddressSpace.CUDA_GLOBAL))) {
      final var toPointer = function(pointer);
      final var convertedPointer = toPointer.newValue(pointer);
      toPointer.entry().append(new Mir.Convert(convertedPointer, new Mir.Constant("text", Ty.STRING)));
      toPointer.entry().terminate(new Mir.Return(convertedPointer));
      reject(toPointer, "Invalid explicit conversion");

      final var fromPointer = function(Ty.STRING, pointer);
      final var parameter = fromPointer.newValue(pointer);
      final var convertedString = fromPointer.newValue(Ty.STRING);
      fromPointer.entry().append(new Mir.Parameter(parameter, 0));
      fromPointer.entry().append(new Mir.Convert(convertedString, parameter));
      fromPointer.entry().terminate(new Mir.Return(convertedString));
      reject(fromPointer, "Invalid explicit conversion");
    }
  }

  @Test
  void unionPayloadsTagsAndWideningAreCheckedStructurally() {
    final var type = new TyUnion(new Ty[]{Ty.INTEGER, Ty.VOID});
    final var function = function(new TyUnion(new Ty[]{Ty.INTEGER, Ty.VOID, Ty.BOOLEAN}));
    final var initial = function.newValue(type);
    final var widened = function.newValue(function.signature().returnType());
    function.entry().append(new Mir.UnionVariant(initial, 1, Mir.Unit.INSTANCE));
    function.entry().append(new Mir.Convert(widened, initial));
    function.entry().terminate(new Mir.Return(widened));
    assertDoesNotThrow(() -> verify(function));

    final var payload = function(type);
    final var badPayload = payload.newValue(type);
    payload.entry().append(new Mir.UnionVariant(badPayload, 1, ONE));
    payload.entry().terminate(new Mir.Return(badPayload));
    reject(payload, "Union payload");

    final var tag = function(type);
    final var badTag = tag.newValue(type);
    tag.entry().append(new Mir.UnionVariant(badTag, 2, ONE));
    tag.entry().terminate(new Mir.Return(badTag));
    reject(tag, "Union tag");
  }

  @Test
  void equivalentUnionTypesDoNotRequireArrayIdentity() {
    final var function = function(new TyUnion(new Ty[]{Ty.INTEGER, Ty.VOID}));
    final var result = function.newValue(new TyUnion(new Ty[]{Ty.INTEGER, Ty.VOID}));
    function.entry().append(new Mir.UnionVariant(result, 0, ONE));
    function.entry().terminate(new Mir.Return(result));
    assertDoesNotThrow(() -> verify(function));
  }

  @Test
  void structCreationAndFieldAccessUseLayoutTypes() {
    final var type = new TyPointer<>(new TyStruct(new TyField[]{new TyField("x", Ty.INTEGER)}));
    final var function = function(Ty.INTEGER);
    final var instance = function.newValue(type);
    function.entry().append(new Mir.NewStruct(instance, List.of(ONE)));
    final var field = new Mir.Field(instance, 0, Ty.INTEGER);
    function.entry().append(new Mir.Store(field, ONE));
    final var loaded = function.newValue(Ty.INTEGER);
    function.entry().append(new Mir.Load(loaded, field));
    function.entry().terminate(new Mir.Return(loaded));
    assertDoesNotThrow(() -> verify(function));

    final var badField = function(Ty.VOID);
    final var badInstance = badField.newValue(type);
    badField.entry().append(new Mir.NewStruct(badInstance, List.of(ONE)));
    badField.entry().append(new Mir.Store(new Mir.Field(badInstance, 1, Ty.INTEGER), ONE));
    done(badField.entry());
    reject(badField, "Field index");
  }

  @Test
  void aggregateLayoutsRetainSourceTypesWhileFieldsUseNormalizedValueTypes() {
    final var inner = new TyStruct(new TyField[]{new TyField("x", Ty.INTEGER)});
    final var optionalInner = new TyUnion(new Ty[]{inner, Ty.VOID});
    final var outer = new TyStruct(new TyField[]{new TyField("nested", optionalInner)});
    final var function = function(MirTypes.valueType(optionalInner));
    final var innerValue = function.newValue(MirTypes.valueType(inner));
    final var optionalValue = function.newValue(MirTypes.valueType(optionalInner));
    final var outerValue = function.newValue(MirTypes.valueType(outer));
    function.entry().append(new Mir.NewStruct(innerValue, List.of(ONE)));
    function.entry().append(new Mir.UnionVariant(optionalValue, 0, innerValue));
    function.entry().append(new Mir.NewStruct(outerValue, List.of(optionalValue)));
    final var loaded = function.newValue(MirTypes.valueType(optionalInner));
    function.entry().append(new Mir.Load(loaded, new Mir.Field(outerValue, 0, loaded.ty())));
    function.entry().terminate(new Mir.Return(loaded));
    assertDoesNotThrow(() -> verify(function));
  }

  @Test
  void arraysRequireTypedInitializersAndIntegerIndices() {
    final var type = new TyPointer<>(new TyValueArray(Ty.INTEGER, 1));
    final var function = function(Ty.INTEGER);
    final var array = function.newValue(type);
    function.entry().append(new Mir.NewArray(array, List.of(ONE), ONE));
    final var loaded = function.newValue(Ty.INTEGER);
    function.entry().append(new Mir.Load(loaded, new Mir.Element(array, new Mir.Constant("0", Ty.INTEGER), Ty.INTEGER)));
    function.entry().terminate(new Mir.Return(loaded));
    assertDoesNotThrow(() -> verify(function));

    final var badIndex = function(Ty.VOID);
    final var badArray = badIndex.newValue(type);
    badIndex.entry().append(new Mir.NewArray(badArray, List.of(ONE), ONE));
    badIndex.entry().append(new Mir.Load(badIndex.newValue(Ty.INTEGER), new Mir.Element(badArray, TRUE, Ty.INTEGER)));
    done(badIndex.entry());
    reject(badIndex, "Array index");

    final var badInitializer = function(Ty.VOID);
    badInitializer.entry().append(new Mir.NewArray(badInitializer.newValue(type), List.of(TRUE), ONE));
    done(badInitializer.entry());
    reject(badInitializer, "Array element");
  }

  @Test
  void arraysMayBeAllocatedWithoutElementInitializers() {
    final var type = new TyPointer<>(new TyValueArray(Ty.INTEGER, 4));
    final var function = function(type);
    final var array = function.newValue(type);
    function.entry().append(new Mir.NewArray(array, List.of(), new Mir.Constant("4", Ty.INTEGER)));
    function.entry().terminate(new Mir.Return(array));
    assertDoesNotThrow(() -> verify(function));
  }

  @Test
  void constantArrayLengthsCannotBeNegativeOrDifferFromTheirStaticAllocation() {
    final var type = new TyPointer<>(new TyValueArray(Ty.INTEGER, 4));
    for (final var length : List.of("-1", "3", "5", "99999999999999999999999999999")) {
      final var function = function(type);
      final var array = function.newValue(type);
      function.entry().append(new Mir.NewArray(array, List.of(ONE), new Mir.Constant(length, Ty.INTEGER)));
      function.entry().terminate(new Mir.Return(array));
      reject(function, length.startsWith("-") ? "must not be negative" : "does not match its static allocation");
    }
  }

  @Test
  void arrayInitializersCannotExceedAStaticallyKnownRequestedLength() {
    final var type = new TyPointer<>(new TyValueArray(Ty.INTEGER, null));
    final var function = function(type);
    final var array = function.newValue(type);
    function.entry().append(new Mir.NewArray(array, List.of(ONE, ONE), ONE));
    function.entry().terminate(new Mir.Return(array));
    reject(function, "initializer count exceeds its requested length");
  }

  @Test
  void arrayInitializersCannotExceedStaticCapacityEvenWithDynamicLength() {
    final var type = new TyPointer<>(new TyValueArray(Ty.INTEGER, 1));
    final var function = function(type, Ty.INTEGER);
    final var length = function.newValue(Ty.INTEGER);
    final var array = function.newValue(type);
    function.entry().append(new Mir.Parameter(length, 0));
    function.entry().append(new Mir.NewArray(array, List.of(ONE, ONE), length));
    function.entry().terminate(new Mir.Return(array));
    reject(function, "initializer count exceeds its static allocation");
  }

  @Test
  void constantArrayLengthChecksRespectTheIntegerRadix() {
    final var type = new TyPointer<>(new TyValueArray(Ty.INTEGER, 4));
    final var function = function(type);
    final var array = function.newValue(type);
    function.entry().append(new Mir.NewArray(array, List.of(), new Mir.Constant("0x4", Ty.INTEGER_HEX)));
    function.entry().terminate(new Mir.Return(array));
    assertDoesNotThrow(() -> verify(function));
  }

  @Test
  void aggregateSourceTypesCannotBeUsedAsUnloweredValues() {
    final var function = function(Ty.VOID);
    function.newLocal("array", new TyValueArray(Ty.INTEGER, 1));
    done(function.entry());
    reject(function, "Not a MIR value type");
  }

  @Test
  void moduleCannotContainTheSameFunctionTwiceOrUseAnExternalScript() {
    final var function = function(Ty.VOID);
    done(function.entry());
    reject(function, "Duplicate module function", function);
    final var external = new MirFunction("main", function.signature(), true);
    reject(external, "script must be");
  }
}
