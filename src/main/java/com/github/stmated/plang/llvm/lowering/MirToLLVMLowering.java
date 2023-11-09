package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.Plang.Result;
import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.mir.model.MirFn;
import com.github.stmated.plang.mir.model.MirFnParameter;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.ty.Ty;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.LongPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.libffi.ffi_cif;
import org.bytedeco.libffi.global.ffi;
import org.bytedeco.llvm.LLVM.LLVMErrorRef;
import org.bytedeco.llvm.LLVM.LLVMOrcLLJITRef;
import org.bytedeco.llvm.global.LLVM;

@Slf4j
public class MirToLLVMLowering {

  static {
    LLVM.LLVMInitializeNativeTarget();
    LLVM.LLVMInitializeNativeAsmPrinter();
  }

  /**
   * Call with a node and it will be wrapped inside a function that will be executed
   */
  public Result lower_script(MirNode node, String name) {

    // TODO: Must find out the return type of the node, and automatically use that instead of INTEGER
    // TODO: Must figure out a way of allowing both "script" form and actual main form. If main method exists, then we use that.
    final var mirFn = new MirFn("script_entry", node, new MirFnParameter[0], false, Ty.INTEGER);

    return lower_fn(mirFn, new Object[0], name);
  }

  /**
   * Call with a function and arguments that will execute it.
   * <p>
   * Should be used later when we have a way of finding the main-method.
   */
  public Result lower_fn(MirFn mirFn, Object[] arguments, String name) {

    final var disposals = new ArrayList<Runnable>();

    final var threadContext = LLVM.LLVMOrcCreateNewThreadSafeContext();

    final var context = LLVM.LLVMOrcThreadSafeContextGetContext(threadContext);
    disposals.add(() -> LLVM.LLVMContextDispose(context));

    final var builder = LLVM.LLVMCreateBuilderInContext(context);
    disposals.add(() -> LLVM.LLVMDisposeBuilder(builder));

    final var module = LLVM.LLVMModuleCreateWithNameInContext(name, context);

    final var functionLoweringRequest = new LLVMFunctionLoweringRequest(
      new Ctx(threadContext, context, builder),
      mirFn,
      result -> LLVM.LLVMLinkModules2(module, result.module())
    );

    LLVMFunctionLowering.lower(functionLoweringRequest);

//    var pm = LLVM.LLVMCreatePassManager();
//    LLVM.LLVMAddAggressiveInstCombinerPass(pm);
//    LLVM.LLVMAddInstructionCombiningPass(pm);
//    LLVM.LLVMAddEarlyCSEPass(pm); // Common Subexpression Elimination
//    LLVM.LLVMAddPromoteMemoryToRegisterPass(pm);
//    LLVM.LLVMAddInstructionCombiningPass(pm);
//    LLVM.LLVMAddReassociatePass(pm); // Change order of operations, making constants ranked better, etc
//    LLVM.LLVMAddNewGVNPass(pm); // Global Value Numbering pass
//    LLVM.LLVMAddCFGSimplificationPass(pm);
//    LLVM.LLVMAddLICMPass(pm); // Loop Invariant Code Motion -- hoise code to header or exit
//    LLVM.LLVMAddIndVarSimplifyPass(pm);
//    LLVM.LLVMAddLoopIdiomPass(); // Replace idioms like zeroing array content with one memset
//    LLVM.LLVMAddLoopUnrollPass(pm);
//    LLVM.LLVMAddAggressiveInstCombinerPass(pm);
//    LLVM.LLVMAddInstructionCombiningPass(pm);
//    LLVM.LLVMAddAggressiveDCEPass(); // Dead Code Elimination

//    LLVM.LLVMRunPassManager(pm, module);

    if (log.isTraceEnabled()) {
      log.trace(LLVM.LLVMPrintModuleToString(module).getString());
    }

    MirToLLVMUtils.verifyModule(module);

    LLVMErrorRef err;
    final var jit = new LLVMOrcLLJITRef();

    try {

      final var jitBuilder = LLVM.LLVMOrcCreateLLJITBuilder();

      Loader.loadGlobal(Loader.load(LLVM.class));
      if ((err = LLVM.LLVMOrcCreateLLJIT(jit, jitBuilder)) != null) {
        final var message = STR."Failed to create LLJIT: \{LLVM.LLVMGetErrorMessage(err).getString()}";
        LLVM.LLVMConsumeError(err);
        throw new RuntimeException(message);
      }

      final var threadSafeModule = LLVM.LLVMOrcCreateNewThreadSafeModule(module, threadContext);
      final var mainDylib = LLVM.LLVMOrcLLJITGetMainJITDylib(jit);
      if ((err = LLVM.LLVMOrcLLJITAddLLVMIRModule(jit, mainDylib, threadSafeModule)) != null) {
        final var message = STR."Failed to add LLVM IR module: \{LLVM.LLVMGetErrorMessage(err).getString()}";
        LLVM.LLVMConsumeError(err);
        throw new RuntimeException(message);
      }

      return callFn(jit, mirFn, arguments);
    } finally {

      LLVM.LLVMOrcDisposeLLJIT(jit);

      // Dispose in reverse order
      for (var i = disposals.size() - 1; i >= 0; i--) {
        disposals.get(i).run();
      }
    }
  }

  private Result callFn(LLVMOrcLLJITRef jit, MirFn mirFn, Object[] arguments) {

    LLVMErrorRef err;
    final var res = new LongPointer(1);
    if ((err = LLVM.LLVMOrcLLJITLookup(jit, res, mirFn.name())) != null) {
      final var message = STR."Failed to look up 'sum' symbol: \{LLVM.LLVMGetErrorMessage(err).getString()}";
      LLVM.LLVMConsumeError(err);
      throw new RuntimeException(message);
    }

    final var argTypes = (mirFn.parameters().length == 0) ? null : new PointerPointer<>(mirFn.parameters().length);
    final var argValues = (mirFn.parameters().length == 0) ? null : new PointerPointer<>(mirFn.parameters().length);
    final var returns = new IntPointer(1);

    for (var i = 0; i < arguments.length; i++) {

      argTypes.put(i, tyToFfiType(arguments[i]));
      argValues.put(i, toFfiValuePointer(arguments[i]));
    }

    final var cif = new ffi_cif();
    if (ffi.ffi_prep_cif(cif, ffi.FFI_DEFAULT_ABI(), mirFn.parameters().length, ffi.ffi_type_sint(), argTypes) != ffi.FFI_OK) {
      throw new RuntimeException("Failed to prepare the libffi cif");
    }

    final var ffiFnPointer = new Pointer() {{
      address = res.get();
    }};

    ffi.ffi_call(cif, ffiFnPointer, returns, argValues);

    final var result = returns.get();

    return new Result(result, "", "");
  }

  private Pointer tyToFfiType(Object o) {

    if (Integer.TYPE.isAssignableFrom(o.getClass())) {
      return ffi.ffi_type_sint();
    } else {
      throw new NotImplementedException(STR."Not implemented ffi type '\{o}'");
    }
  }

  private Pointer toFfiValuePointer(Object o) {

    if (Integer.TYPE.isAssignableFrom(o.getClass())) {
      return new IntPointer(1).put((Integer) o);
    } else {
      throw new NotImplementedException(STR."Not implemented ffi type '\{o}'");
    }
  }
}
