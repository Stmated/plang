package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.Plang.Result;
import com.github.stmated.plang.PlangRunOptions;
import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.llvm.util.LLVMTys;
import com.github.stmated.plang.mir.Mir;
import com.github.stmated.plang.mir.Mir.InstrCreateFn;
import com.github.stmated.plang.mir.MirIdentifierId;
import com.github.stmated.plang.mir.MirLoweringResult;
import com.github.stmated.plang.mir.MirNodeTyPass;
import com.github.stmated.plang.mir.ThirToMirLowering;
import com.github.stmated.plang.mir.model.MirFnParameter;
import com.github.stmated.plang.mir.model.MirFnSignature;
import com.github.stmated.plang.ty.RealKind;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyFn;
import com.github.stmated.plang.ty.TyValueNumberInteger;
import com.github.stmated.plang.ty.TyValueNumberPrecisioned;
import com.github.stmated.plang.ty.util.Tys;
import java.util.ArrayList;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.BooleanPointer;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.CharPointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.LongPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacpp.ShortPointer;
import org.bytedeco.libffi.ffi_cif;
import org.bytedeco.libffi.ffi_type;
import org.bytedeco.libffi.global.ffi;
import org.bytedeco.llvm.LLVM.LLVMErrorRef;
import org.bytedeco.llvm.LLVM.LLVMOrcLLJITRef;
import org.bytedeco.llvm.LLVM.LLVMTargetRef;
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
  public <T> Result<T> lower_script(MirLoweringResult mirResult, String name, PlangRunOptions opt) {

    final MirFnSignature fnSignature;
    final var initNode = mirResult.initNode();
    if (initNode.instructions().getLast() instanceof Mir.InstrReturn ret && ret.instr() instanceof Mir.InstrCreateFn createFn) {

      // Return type of the script is a function, we will make it main and call it later with our arguments
      if (createFn.name() == null) {
        createFn.name(new MirIdentifierId("main", null, 0)); // TODO: Wrong
      }

      return lower_fn(createFn, opt.arguments(), name, opt.optLevel());

    } else {

      // The return type is not a function, so we will create a main function and wrap our instructions inside it.
      if (opt.arguments() != null && opt.arguments().length > 0) {
        throw new IllegalArgumentException("Not allowed to send arguments to a script that does not return a function");
      }

      final var mirFnTy = Objects.requireNonNull(mirResult.initNode().ty().value(), STR."You must run \{MirNodeTyPass.class.getSimpleName()}");
      final var simplifiedTy = Tys.simplify(mirFnTy);
      final var llvmTy = LLVMTys.normalize(simplifiedTy);

      fnSignature = new MirFnSignature(new MirFnParameter[0], false, llvmTy);

      final var mirFn = new InstrCreateFn(mirResult.initNode(), fnSignature, ThirToMirLowering.signatureToTy(fnSignature));
      final var mirFnName = Objects.requireNonNullElse(mirResult.initNode().name(), "main");
      mirFn.name(new MirIdentifierId(mirFnName, null, 0)); // TODO: Wrong

      return lower_fn(mirFn, opt.arguments(), name, opt.optLevel());
    }
  }

  /**
   * Call with a function and arguments that will execute it.
   * <p>
   * Should be used later when we have a way of finding the main-method.
   */
  public <T> Result<T> lower_fn(InstrCreateFn instrCreateFn, Object[] arguments, String name, int optLevel) {

    final var disposals = new ArrayList<Runnable>();

    final var threadContext = LLVM.LLVMOrcCreateNewThreadSafeContext();

    final var context = LLVM.LLVMOrcThreadSafeContextGetContext(threadContext);
    disposals.add(() -> LLVM.LLVMContextDispose(context));

    final var builder = LLVM.LLVMCreateBuilderInContext(context);
    disposals.add(() -> LLVM.LLVMDisposeBuilder(builder));

    final var module = LLVM.LLVMModuleCreateWithNameInContext(name, context);

    final var functionLoweringRequest = new LLVMFunctionLoweringRequest(
      new MirToLLVMCtx(threadContext, context, builder),
      instrCreateFn,
      name,
      result -> LLVM.LLVMLinkModules2(module, result.module())
    );

    LLVMFunctionLowering.lower(functionLoweringRequest);

    final var passBuilderOptions = LLVM.LLVMCreatePassBuilderOptions();

    final var cpuName = LLVM.LLVMGetHostCPUName();
    final var cpuFeatures = LLVM.LLVMGetHostCPUFeatures();
    final var targetTriple = LLVM.LLVMGetDefaultTargetTriple();

    LLVMTargetRef targetRef = new LLVMTargetRef();

    final var errorPointer = new BytePointer();
    if (LLVM.LLVMGetTargetFromTriple(targetTriple, targetRef, errorPointer) != 0) {
      var i = 0;
    }

    final var targetMachine = LLVM.LLVMCreateTargetMachine(
      targetRef, targetTriple, cpuName, cpuFeatures,
      switch ((Integer) optLevel) {
        case Integer i when i <= 0 -> LLVM.LLVMCodeGenLevelNone;
        case 1 -> LLVM.LLVMCodeGenLevelLess;
        case 2 -> LLVM.LLVMCodeGenLevelDefault;
        default -> LLVM.LLVMCodeGenLevelAggressive;
      },
      LLVM.LLVMRelocDefault,
      LLVM.LLVMCodeModelDefault
    );

    LLVM.LLVMRunPasses(module, STR."default<O\{optLevel}>", targetMachine, passBuilderOptions);

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

      return callFn(jit, instrCreateFn, arguments);
    } finally {

      LLVM.LLVMOrcDisposeLLJIT(jit);

      // Dispose in reverse order
      for (var i = disposals.size() - 1; i >= 0; i--) {
        disposals.get(i).run();
      }
    }
  }

  private <T> Result<T> callFn(LLVMOrcLLJITRef jit, InstrCreateFn instrCreateFn, Object[] arguments) {

    LLVMErrorRef err;
    final var res = new LongPointer(1);
    final var fnName = instrCreateFn.name().getUniqueName();
    if ((err = LLVM.LLVMOrcLLJITLookup(jit, res, fnName)) != null) {
      final var message = STR."Failed to look up function symbol: \{LLVM.LLVMGetErrorMessage(err).getString()}";
      throw new RuntimeException(message);
    }

    final var argTypes = (instrCreateFn.signature().parameters().length == 0) ? null : new PointerPointer<>(instrCreateFn.signature().parameters().length);
    final var argValues = (instrCreateFn.signature().parameters().length == 0) ? null : new PointerPointer<>(instrCreateFn.signature().parameters().length);
    final var javaReturnType = toJavaType(instrCreateFn.signature().returnType());
    final var ffiReturnType = tyToFfiType(javaReturnType);
    final var returnPointer = toFfiValuePointer(javaReturnType);

    if (arguments != null) {
      for (var i = 0; i < arguments.length; i++) {

        argTypes.put(i, tyToFfiType(arguments[i].getClass()));
        argValues.put(i, toFfiValuePointer(arguments[i]));
      }
    }

    final var cif = new ffi_cif();

    if (ffi.ffi_prep_cif(cif, ffi.FFI_DEFAULT_ABI(), instrCreateFn.signature().parameters().length, ffiReturnType, argTypes) != ffi.FFI_OK) {
      throw new RuntimeException("Failed to prepare the libffi cif");
    }

    final var ffiFnPointer = new Pointer() {{
      address = res.get();
    }};

    ffi.ffi_call(cif, ffiFnPointer, returnPointer, argValues);

    final var result = (T) getValue(returnPointer, instrCreateFn.signature().returnType());
    return new Result<>(result, "", "");
  }

  private Object getValue(Pointer pointer, Ty ty) {
    if (pointer instanceof IntPointer p) {
      return p.get();
    } else if (pointer instanceof FloatPointer p) {
      return p.get();
    } else if (pointer instanceof DoublePointer p) {
      return p.get();
    } else if (pointer instanceof LongPointer p) {
      return p.get();
    } else if (pointer instanceof ShortPointer p) {
      return p.get();
    } else if (pointer instanceof CharPointer p) {
      return p.get();
    } else if (pointer instanceof BooleanPointer p) {
      return p.get();
    } else if (pointer instanceof BytePointer p) {
      return p.get();
    } else {
      throw new NotImplementedException(STR."Have no implemented gettign value of '\{pointer}'");
    }
  }

  private ffi_type tyToFfiType(Class<?> clazz) {

    if (Integer.class.isAssignableFrom(clazz)) {
      return ffi.ffi_type_sint();
    } else if (Double.class.isAssignableFrom(clazz)) {
      return ffi.ffi_type_double();
    } else if (Float.class.isAssignableFrom(clazz)) {
      return ffi.ffi_type_float();
    } else {
      throw new NotImplementedException(STR."Not implemented ffi type of '\{clazz}'");
    }
  }

  private Class<?> toJavaType(Ty ty) {

    if (ty instanceof TyValueNumberInteger) {
      return Integer.class;
    } else if (ty instanceof TyValueNumberPrecisioned vnp) {
      if (vnp.kind() == RealKind.FLOAT) {
        return Float.class;
      } else {
        return Double.class;
      }
    } else {
      throw new NotImplementedException(STR."Not implemented ty conversion for '\{ty}'");
    }
  }

  private Pointer toFfiValuePointer(Object o) {

    if (Integer.class.isAssignableFrom(o.getClass())) {
      return new IntPointer(1).put((Integer) o);
    } else if (Float.class.isAssignableFrom(o.getClass())) {
      return new FloatPointer(1).put((Float) o);
    } else if (Double.class.isAssignableFrom(o.getClass())) {
      return new DoublePointer(1).put((Double) o);
    } else {
      throw new NotImplementedException(STR."Not implemented ffi type '\{o}'");
    }
  }

  private Pointer toFfiValuePointer(Class<?> clazz) {

    if (Integer.class.isAssignableFrom(clazz)) {
      return new IntPointer(1);
    } else if (Float.class.isAssignableFrom(clazz)) {
      return new FloatPointer(1);
    } else if (Double.class.isAssignableFrom(clazz)) {
      return new DoublePointer(1);
    } else {
      throw new NotImplementedException(STR."Not implemented ffi type '\{clazz}'");
    }
  }
}
