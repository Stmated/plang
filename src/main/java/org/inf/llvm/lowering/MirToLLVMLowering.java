package org.inf.llvm.lowering;

import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.*;
import org.bytedeco.libffi.ffi_cif;
import org.bytedeco.libffi.ffi_type;
import org.bytedeco.libffi.global.ffi;
import org.bytedeco.llvm.LLVM.*;
import org.bytedeco.llvm.global.LLVM;
import org.inf.Inf.Result;
import org.inf.InfRunOptions;
import org.inf.Main;
import org.inf.exceptions.NotImplementedException;
import org.inf.mir.MirLoweringResult;
import org.inf.mir.MirTypes;
import org.inf.mir.MirUnionValue;
import org.inf.mir.MirVerifier;
import org.inf.ty.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
public class MirToLLVMLowering {

  static {
    LLVM.LLVMInitializeNativeTarget();
    LLVM.LLVMInitializeNativeAsmPrinter();
  }

  /**
   * The native entry wrapper executes script setup, optionally invokes its returned function,
   * and writes its result through an output pointer. This avoids platform-specific aggregate
   * return ABIs at the libffi boundary without changing the MIR.
   */
  public <T> Result<T> lower_script(MirLoweringResult input, String name, InfRunOptions options) {
    MirVerifier.verify(input);
    final var scriptType = MirTypes.functionType(input.script().signature());
    final var invokedType = scriptType.returnTy() instanceof TyPointer<?> pointer && pointer.inner() instanceof TyFn fn ? fn : scriptType;
    final var parameters = invokedType.parameters();
    final var arguments = options.arguments() == null ? new Object[0] : options.arguments();
    if (invokedType.vararg()) {
      throw new NotImplementedException("Calling a variadic entry function from Java is unsupported");
    }
    if (arguments.length != parameters.length) {
      throw new IllegalArgumentException("Expected " + parameters.length + " script arguments, got " + arguments.length);
    }
    requireResultType(invokedType.returnTy());
    final var context = LLVM.LLVMContextCreate();
    final var threadContext = LLVM.LLVMOrcCreateNewThreadSafeContextFromLLVMContext(context);
    final var builder = LLVM.LLVMCreateBuilderInContext(context);
    final var module = LLVM.LLVMModuleCreateWithNameInContext(name, context);
    final var jit = new LLVMOrcLLJITRef();
    var transferred = false;
    try {
      check(LLVM.LLVMOrcCreateLLJIT(jit, LLVM.LLVMOrcCreateLLJITBuilder()));
      LLVM.LLVMSetDataLayout(module, LLVM.LLVMOrcLLJITGetDataLayoutStr(jit));
      LLVM.LLVMSetTarget(module, LLVM.LLVMOrcLLJITGetTripleString(jit));
      final var lowering = new LLVMFunctionLowering(context, builder, module);
      lowering.lower(input);
      final var types = lowering.types;
      final var wrapperParameters = new LLVMTypeRef[parameters.length + 1];
      for (var i = 0; i < parameters.length; i++) {
        wrapperParameters[i] = types.resolve(parameters[i].ty());
      }
      wrapperParameters[parameters.length] = LLVM.LLVMPointerTypeInContext(context, 0);
      final var wrapperType = LLVM.LLVMFunctionType(types.resolve(Ty.VOID), new PointerPointer<>(wrapperParameters), wrapperParameters.length, 0);
      final var wrapper = LLVM.LLVMAddFunction(module, "__inf_invoke", wrapperType);
      final var wrapperName = LLVM.LLVMGetValueName(wrapper).getString();
      LLVM.LLVMPositionBuilderAtEnd(builder, LLVM.LLVMAppendBasicBlockInContext(context, wrapper, "entry"));
      final var scriptValue = LLVM.LLVMBuildCall2(builder, types.resolve(scriptType), lowering.function(input.script()),
        new PointerPointer<LLVMValueRef>(0), 0, scriptType.returnTy().equals(Ty.VOID) ? "" : "script");
      var result = scriptValue;
      if (invokedType != scriptType) {
        final var callArguments = new LLVMValueRef[parameters.length];
        for (var i = 0; i < callArguments.length; i++) {
          callArguments[i] = LLVM.LLVMGetParam(wrapper, i);
        }
        result = LLVM.LLVMBuildCall2(builder, types.resolve(invokedType), scriptValue, new PointerPointer<>(callArguments),
          callArguments.length, invokedType.returnTy().equals(Ty.VOID) ? "" : "result");
      }
      if (!invokedType.returnTy().equals(Ty.VOID)) {
        LLVM.LLVMBuildStore(builder, result, LLVM.LLVMGetParam(wrapper, parameters.length));
      }
      LLVM.LLVMBuildRetVoid(builder);
      if (options.includeCppLibs()) {
        linkClangWrapper(context, module);
      }
      LLVMUtils.verifyModule(module);
      optimize(module, options.optLevel());
      LLVMUtils.verifyModule(module);
      if (log.isTraceEnabled()) {
        final var text = LLVM.LLVMPrintModuleToString(module);
        log.trace(text.getString());
        LLVM.LLVMDisposeMessage(text);
      }
      final var mainDylib = LLVM.LLVMOrcLLJITGetMainJITDylib(jit);
      final var generator = new LLVMOrcDefinitionGeneratorRef();
      check(LLVM.LLVMOrcCreateDynamicLibrarySearchGeneratorForProcess(generator, LLVM.LLVMOrcLLJITGetGlobalPrefix(jit), null, null));
      LLVM.LLVMOrcJITDylibAddGenerator(mainDylib, generator);
      // Layout information is read before transferring module ownership to ORC.
      final var layout = LLVM.LLVMCreateTargetData(LLVM.LLVMOrcLLJITGetDataLayoutStr(jit));
      try {
        final var decoder = decoder(invokedType.returnTy(), types, layout);
        final var threadModule = LLVM.LLVMOrcCreateNewThreadSafeModule(module, threadContext);
        transferred = true;
        check(LLVM.LLVMOrcLLJITAddLLVMIRModule(jit, mainDylib, threadModule));
        return call(jit, wrapperName, lowering.cleanupName(), parameters, arguments, decoder);
      } finally {
        LLVM.LLVMDisposeTargetData(layout);
      }
    } finally {
      LLVM.LLVMDisposeBuilder(builder);
      if (!transferred) {
        LLVM.LLVMDisposeModule(module);
      }
      if (!jit.isNull()) {
        check(LLVM.LLVMOrcDisposeLLJIT(jit));
      }
      LLVM.LLVMOrcDisposeThreadSafeContext(threadContext);
    }
  }

  private static void check(LLVMErrorRef error) {
    if (error != null && !error.isNull()) {
      final var message = LLVM.LLVMGetErrorMessage(error);
      final var text = message.getString();
      LLVM.LLVMDisposeErrorMessage(message);
      throw new IllegalStateException(text);
    }
  }

  private static void optimize(LLVMModuleRef module, int level) {
    if (level < 0 || level > 3) {
      throw new IllegalArgumentException("Optimization level must be between 0 and 3");
    }
    final var triple = LLVM.LLVMGetDefaultTargetTriple();
    final var cpu = LLVM.LLVMGetHostCPUName();
    final var features = LLVM.LLVMGetHostCPUFeatures();
    final var target = new LLVMTargetRef();
    final var error = new BytePointer();
    if (LLVM.LLVMGetTargetFromTriple(triple, target, error) != 0) {
      final var text = error.getString();
      LLVM.LLVMDisposeMessage(error);
      throw new IllegalStateException(text);
    }
    final var machine = LLVM.LLVMCreateTargetMachine(target, triple, cpu, features,
      LLVM.LLVMCodeGenLevelDefault, LLVM.LLVMRelocDefault, LLVM.LLVMCodeModelDefault);
    final var passOptions = LLVM.LLVMCreatePassBuilderOptions();
    try {
      check(LLVM.LLVMRunPasses(module, "default<O" + level + ">", machine, passOptions));
    } finally {
      LLVM.LLVMDisposePassBuilderOptions(passOptions);
      LLVM.LLVMDisposeTargetMachine(machine);
      LLVM.LLVMDisposeMessage(triple);
      LLVM.LLVMDisposeMessage(cpu);
      LLVM.LLVMDisposeMessage(features);
    }
  }

  private record Decoder(long size, Function<ByteBuffer, Object> read) {
  }

  private static Decoder decoder(Ty ty, LLVMTypeResolver types, LLVMTargetDataRef layout) {
    if (ty.equals(Ty.VOID)) {
      return new Decoder(1, _ -> null);
    }
    final var llvmType = types.resolve(ty);
    final var size = LLVM.LLVMABISizeOfType(layout, llvmType);
    if (ty instanceof TyUnion union) {
      final var variants = union.types();
      final var decoders = new Decoder[variants.length];
      final var offsets = new int[variants.length];
      for (var i = 0; i < variants.length; i++) {
        decoders[i] = decoder(variants[i], types, layout);
        if (!variants[i].equals(Ty.VOID)) {
          offsets[i] = Math.toIntExact(LLVM.LLVMOffsetOfElement(layout, llvmType, LLVMFunctionLowering.payloadIndex(union, i)));
        }
      }
      return new Decoder(size, buffer -> {
        final var tag = buffer.getInt(0);
        if (tag < 0 || tag >= variants.length) {
          throw new IllegalStateException("Invalid native union tag " + tag);
        }
        final var payload = buffer.slice(offsets[tag], Math.toIntExact(decoders[tag].size())).order(ByteOrder.nativeOrder());
        return new MirUnionValue(union, tag, decoders[tag].read().apply(payload));
      });
    }
    return new Decoder(size, buffer -> switch (ty) {
      case TyValueBoolean _ -> buffer.get(0) != 0;
      case TyValueNumberInteger integer -> switch (integer.width().value()) {
        case 8 -> integer.signed() ? (int) buffer.get(0) : Byte.toUnsignedInt(buffer.get(0));
        case 16 -> integer.signed() ? (int) buffer.getShort(0) : Short.toUnsignedInt(buffer.getShort(0));
        case 32 -> buffer.getInt(0);
        case 64 -> buffer.getLong(0);
        case 128 -> {
          final var bytes = new byte[16];
          buffer.get(0, bytes);
          if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            for (var i = 0; i < bytes.length / 2; i++) {
              final var first = bytes[i];
              bytes[i] = bytes[bytes.length - i - 1];
              bytes[bytes.length - i - 1] = first;
            }
          }
          yield integer.signed() ? new java.math.BigInteger(bytes) : new java.math.BigInteger(1, bytes);
        }
        default -> throw new NotImplementedException("Java integer return width " + integer.width());
      };
      case TyValueNumberPrecisioned real when real.width().value() == 32 -> buffer.getFloat(0);
      case TyValueNumberPrecisioned real when real.width().value() == 64 -> buffer.getDouble(0);
      case TyValueString _, TyPointer<?> _ -> {
        final var address = buffer.getLong(0);
        yield address == 0 ? null : new BytePointer(pointer(address)).getString(StandardCharsets.UTF_8);
      }
      default -> throw new NotImplementedException("Java result decoding for " + ty);
    });
  }

  private static void requireResultType(Ty type) {
    if (type.equals(Ty.VOID) || type instanceof TyValueBoolean || isString(type)) {
      return;
    }
    if (type instanceof TyValueNumberInteger integer && List.of(8, 16, 32, 64, 128).contains(integer.width().value()) ||
        type instanceof TyValueNumberPrecisioned real && List.of(32, 64).contains(real.width().value())) {
      return;
    }
    if (type instanceof TyUnion union) {
      for (final var variant : union.types()) {
        requireResultType(variant);
      }
      return;
    }
    throw new NotImplementedException("Returning " + type + " to Java is unsupported");
  }

  private static boolean isString(Ty ty) {
    return ty instanceof TyValueString || ty instanceof TyPointer<?> pointer && pointer.inner().equals(Ty.CHAR);
  }

  @SuppressWarnings("unchecked")
  private static <T> Result<T> call(LLVMOrcLLJITRef jit, String name, String cleanupName, TyParam[] parameters, Object[] arguments, Decoder decoder) {
    final var resources = new ArrayList<Pointer>();
    try {
      final var symbol = keep(resources, new LongPointer(1));
      check(LLVM.LLVMOrcLLJITLookup(jit, symbol, name));
      final var count = parameters.length + 1;
      final var argTypes = keep(resources, new PointerPointer<ffi_type>(count));
      final var argValues = keep(resources, new PointerPointer<Pointer>(count));
      for (var i = 0; i < parameters.length; i++) {
        argTypes.put(i, ffiType(parameters[i].ty()));
        argValues.put(i, argument(parameters[i].ty(), arguments[i], resources));
      }
      final var output = keep(resources, new BytePointer(Math.max(1, decoder.size())));
      argTypes.put(parameters.length, ffi.ffi_type_pointer());
      argValues.put(parameters.length, keep(resources, new PointerPointer<>(new Pointer[]{output})));
      final var cif = keep(resources, new ffi_cif());
      if (ffi.ffi_prep_cif(cif, ffi.FFI_DEFAULT_ABI(), count, ffi.ffi_type_void(), argTypes) != ffi.FFI_OK) {
        throw new IllegalStateException("Failed to prepare libffi call");
      }
      final var cleanupSymbol = keep(resources, new LongPointer(1));
      final var cleanupCif = keep(resources, new ffi_cif());
      if (cleanupName != null) {
        check(LLVM.LLVMOrcLLJITLookup(jit, cleanupSymbol, cleanupName));
        if (ffi.ffi_prep_cif(cleanupCif, ffi.FFI_DEFAULT_ABI(), 0, ffi.ffi_type_void(), (PointerPointer<?>) null) != ffi.FFI_OK) {
          throw new IllegalStateException("Failed to prepare aggregate cleanup");
        }
      }
      try {
        ffi.ffi_call(cif, pointer(symbol.get()), null, argValues);
        return new Result<>((T) decoder.read().apply(output.asByteBuffer().order(ByteOrder.nativeOrder())), "", "");
      } finally {
        if (cleanupName != null) {
          ffi.ffi_call(cleanupCif, pointer(cleanupSymbol.get()), null, null);
        }
      }
    } finally {
      for (var i = resources.size() - 1; i >= 0; i--) {
        resources.get(i).close();
      }
    }
  }

  private static Pointer pointer(long value) {
    return new Pointer() {{
      address = value;
    }};
  }

  private static <P extends Pointer> P keep(List<Pointer> resources, P value) {
    resources.add(value);
    return value;
  }

  private static ffi_type ffiType(Ty ty) {
    if (isString(ty)) {
      return ffi.ffi_type_pointer();
    }
    return switch (ty) {
      case TyValueBoolean _ -> ffi.ffi_type_uint8();
      case TyValueNumberInteger integer -> switch (integer.width().value()) {
        case 8 -> integer.signed() ? ffi.ffi_type_sint8() : ffi.ffi_type_uint8();
        case 16 -> integer.signed() ? ffi.ffi_type_sint16() : ffi.ffi_type_uint16();
        case 32 -> integer.signed() ? ffi.ffi_type_sint32() : ffi.ffi_type_uint32();
        case 64 -> integer.signed() ? ffi.ffi_type_sint64() : ffi.ffi_type_uint64();
        default -> throw new NotImplementedException("FFI argument integer width " + integer.width());
      };
      case TyValueNumberPrecisioned real when real.width().value() == 32 -> ffi.ffi_type_float();
      case TyValueNumberPrecisioned real when real.width().value() == 64 -> ffi.ffi_type_double();
      default -> throw new NotImplementedException("FFI argument type " + ty);
    };
  }

  private static Pointer argument(Ty ty, Object value, List<Pointer> resources) {
    if (isString(ty)) {
      final var string = keep(resources, new BytePointer(Objects.toString(value), StandardCharsets.UTF_8));
      return keep(resources, new PointerPointer<>(new Pointer[]{string}));
    }
    return switch (ty) {
      case TyValueBoolean _ -> keep(resources, new BytePointer(1).put((byte) ((Boolean) value ? 1 : 0)));
      case TyValueNumberInteger integer -> switch (integer.width().value()) {
        case 8 -> keep(resources, new BytePointer(1).put(((Number) value).byteValue()));
        case 16 -> keep(resources, new ShortPointer(1).put(((Number) value).shortValue()));
        case 32 -> keep(resources, new IntPointer(1).put(((Number) value).intValue()));
        case 64 -> keep(resources, new LongPointer(1).put(((Number) value).longValue()));
        default -> throw new NotImplementedException("FFI integer argument " + integer);
      };
      case TyValueNumberPrecisioned real when real.width().value() == 32 -> keep(resources, new FloatPointer(1).put(((Number) value).floatValue()));
      case TyValueNumberPrecisioned real when real.width().value() == 64 -> keep(resources, new DoublePointer(1).put(((Number) value).doubleValue()));
      default -> throw new NotImplementedException("FFI argument type " + ty);
    };
  }

  private static void linkClangWrapper(LLVMContextRef context, LLVMModuleRef module) {
    final var wrapperPath = "src/main/cpp/" + Main.class.getPackageName().replace('.', '/');
    final var sourceFile = new File(wrapperPath, "wrapper.cc").getAbsoluteFile();
    final var bitCodeFile = new File(wrapperPath, "wrapper.bc").getAbsoluteFile();
    if (!bitCodeFile.exists()) {
      try {
        final var process = new ProcessBuilder("clang++", "-emit-llvm", "-c", sourceFile.getAbsolutePath(), "-o", bitCodeFile.getAbsolutePath())
          .redirectErrorStream(true).start();
        final var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
          throw new IllegalArgumentException("Compilation error: " + output);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      } catch (java.io.IOException e) {
        throw new IllegalStateException(e);
      }
    }
    final var buffer = new LLVMMemoryBufferRef();
    final var message = new BytePointer();
    try (final var path = new BytePointer(bitCodeFile.getAbsolutePath())) {
      if (LLVM.LLVMCreateMemoryBufferWithContentsOfFile(path, buffer, message) != 0) {
        final var text = message.getString();
        LLVM.LLVMDisposeMessage(message);
        throw new IllegalArgumentException(text);
      }
    }
    try {
      final var wrapper = new LLVMModuleRef();
      if (LLVM.LLVMParseBitcodeInContext2(context, buffer, wrapper) != 0 || LLVM.LLVMLinkModules2(module, wrapper) != 0) {
        throw new IllegalArgumentException("Could not parse/link C++ wrapper");
      }
    } finally {
      LLVM.LLVMDisposeMemoryBuffer(buffer);
    }
  }
}
