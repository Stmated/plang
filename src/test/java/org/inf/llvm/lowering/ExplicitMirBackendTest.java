package org.inf.llvm.lowering;

import org.bytedeco.llvm.global.LLVM;
import org.inf.Inf;
import org.inf.InfRunOptions;
import org.inf.mir.*;
import org.inf.mir.model.*;
import org.inf.ty.*;
import org.inf.ty.util.MachineTarget;
import org.inf.ty.util.Tys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExplicitMirBackendTest {

  private static MirFunction function(String name, Ty result, MirFnParameter... parameters) {
    return new MirFunction(name, new MirFnSignature(parameters, false, result), false);
  }

  private static Object run(MirFunction script, List<MirFunction> functions, Object... arguments) {
    return new MirToLLVMLowering().lower_script(new MirLoweringResult(script, functions), "explicit_mir_test",
      InfRunOptions.builder().arguments(arguments).optLevel(2).build()).resultValue();
  }

  @Test
  void wrapperExecutesSetupBeforeCallingReturnedFunction() {
    final var entry = function("increment", Ty.INTEGER, new MirFnParameter("x", Ty.INTEGER));
    final var parameter = entry.newValue(Ty.INTEGER);
    final var sum = entry.newValue(Ty.INTEGER);
    entry.entry().append(new Mir.Parameter(parameter, 0));
    entry.entry().append(new Mir.Binary(sum, parameter, MirBinaryOperationKind.ADD, new Mir.Constant("1", Ty.INTEGER)));
    entry.entry().terminate(new Mir.Return(sum));
    final var script = function("script", new Mir.FunctionRef(entry).ty());
    final var slot = script.newLocal("setup", Ty.INTEGER);
    script.entry().append(new Mir.Store(slot, new Mir.Constant("42", Ty.INTEGER)));
    script.entry().terminate(new Mir.Return(new Mir.FunctionRef(entry)));
    assertEquals(10, run(script, List.of(script, entry), 9));
    assertEquals(1, script.entry().instructions().size(), "Invocation must not mutate the MIR");
  }

  @Test
  void unionWideningRetagsInsteadOfReinterpretingPayload() {
    final var result = assertInstanceOf(MirUnionValue.class, Inf.codeToResult("""
      val narrow: int | bool = 42;
      val wide: string | bool | int = narrow;
      wide
      """, InfRunOptions.builder().optLevel(2).build()).resultValue());
    assertAll(
      () -> assertArrayEquals(new Ty[]{Ty.STRING, Ty.BOOLEAN, Tys.fromString("int", new MachineTarget(64))},
        result.type().types()),
      () -> assertEquals(2, result.variant()),
      () -> assertEquals(42, result.payload())
    );
  }

  @Test
  void voidUnionVariantHasNoPayload() {
    final var result = assertInstanceOf(MirUnionValue.class,
      Inf.codeToResult("if (false) 42", InfRunOptions.builder().optLevel(2).build()).resultValue());
    assertAll(
      () -> assertArrayEquals(new Ty[]{Ty.INTEGER, Ty.VOID}, result.type().types()),
      () -> assertEquals(1, result.variant()),
      () -> assertNull(result.payload())
    );
  }

  @Test
  void ffiBoundarySupportsBooleanAndVoid() {
    final var bool = function("bool_script", Ty.BOOLEAN);
    bool.entry().terminate(new Mir.Return(new Mir.Constant("true", Ty.BOOLEAN)));
    assertEquals(true, run(bool, List.of(bool)));
    final var unit = function("void_script", Ty.VOID);
    unit.entry().terminate(new Mir.Return(Mir.Unit.INSTANCE));
    assertNull(run(unit, List.of(unit)));
  }

  @Test
  void serializationUsesDominanceNotBlockCreationOrder() {
    final var script = function("script", Ty.INTEGER);
    final var use = script.newBlock("use");
    final var definition = script.newBlock("definition");
    final var value = script.newValue(Ty.INTEGER);
    script.entry().terminate(new Mir.Jump(definition));
    definition.append(new Mir.Binary(value, new Mir.Constant("20", Ty.INTEGER),
      MirBinaryOperationKind.ADD, new Mir.Constant("22", Ty.INTEGER)));
    definition.terminate(new Mir.Jump(use));
    use.terminate(new Mir.Return(value));
    assertEquals(42, run(script, List.of(script)));
  }

  @Test
  void aggregateReturnedFromLanguageFunctionLivesUntilInvocationCompletes() {
    final var arrayType = new TyPointer<>(new TyValueArray(Ty.INTEGER, 2));
    final var make = function("make_array", arrayType);
    final var allocation = make.newValue(arrayType);
    make.entry().append(new Mir.NewArray(allocation, List.of(new Mir.Constant("10", Ty.INTEGER), new Mir.Constant("20", Ty.INTEGER)),
      new Mir.Constant("2", Ty.INTEGER)));
    make.entry().terminate(new Mir.Return(allocation));
    final var script = function("script", Ty.INTEGER);
    final var array = script.newValue(arrayType);
    final var element = script.newValue(Ty.INTEGER);
    script.entry().append(new Mir.Call(array, new Mir.FunctionRef(make), make.signature(), List.of()));
    script.entry().append(new Mir.Load(element, new Mir.Element(array, new Mir.Constant("1", Ty.INTEGER), Ty.INTEGER)));
    script.entry().terminate(new Mir.Return(element));
    assertEquals(20, run(script, List.of(script, make)));
  }

  @ParameterizedTest
  @ValueSource(ints = {32, 64})
  void allocatorUsesTargetSizeTWithoutExecutingLargeAllocation(int pointerWidth) {
    final var script = function("script", Ty.VOID);
    final var arrayType = new TyPointer<>(new TyValueArray(Ty.INTEGER, 1073741824));
    script.entry().append(new Mir.NewArray(script.newValue(arrayType),
      List.of(new Mir.Constant("1", Ty.INTEGER)), new Mir.Constant("1073741824", Ty.INTEGER)));
    script.entry().terminate(new Mir.Return(Mir.Unit.INSTANCE));
    final var context = LLVM.LLVMContextCreate();
    final var builder = LLVM.LLVMCreateBuilderInContext(context);
    final var module = LLVM.LLVMModuleCreateWithNameInContext("allocator_width", context);
    try {
      LLVM.LLVMSetDataLayout(module, "e-p:" + pointerWidth + ":" + pointerWidth);
      new LLVMFunctionLowering(context, builder, module).lower(new MirLoweringResult(script, List.of(script)));
      LLVMUtils.verifyModule(module);
      final var malloc = LLVM.LLVMGetNamedFunction(module, "malloc");
      assertEquals(pointerWidth, LLVM.LLVMGetIntTypeWidth(LLVM.LLVMTypeOf(LLVM.LLVMGetParam(malloc, 0))));
    } finally {
      LLVM.LLVMDisposeBuilder(builder);
      LLVM.LLVMDisposeModule(module);
      LLVM.LLVMContextDispose(context);
    }
  }
}
