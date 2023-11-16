package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.exceptions.GenericLLVMException;
import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.exceptions.UnreachableCodeLLVMException;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyFn;
import com.github.stmated.plang.ty.TyPointer;
import com.github.stmated.plang.ty.TyPointerAddressSpace;
import com.github.stmated.plang.ty.TyValueArray;
import com.github.stmated.plang.ty.TyValueBoolean;
import com.github.stmated.plang.ty.TyValueNumberInteger;
import com.github.stmated.plang.ty.TyValueNumberPrecisioned;
import com.github.stmated.plang.ty.TyValueNumberScaled;
import com.github.stmated.plang.ty.TyValueString;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.experimental.UtilityClass;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

@UtilityClass
public class MirToLLVMUtils {

  public static LLVMTypeRef toLLVMType(MirToLLVMCtx mirToLlvmCtx, Ty ty) {
    return toLLVMType(mirToLlvmCtx.context, ty);
  }

  public static LLVMTypeRef toLLVMType(LLVMContextRef context, Ty ty) {

    return switch (ty) {
      case TyValueNumberInteger ni when ni.width().value() == 128 -> LLVM.LLVMInt128TypeInContext(context);
      case TyValueNumberInteger ni when ni.width().value() == 64 -> LLVM.LLVMInt64TypeInContext(context);
      case TyValueNumberInteger ni when ni.width().value() == 32 -> LLVM.LLVMInt32TypeInContext(context);
      case TyValueNumberInteger ni when ni.width().value() == 16 -> LLVM.LLVMInt16TypeInContext(context);
      case TyValueNumberInteger ni when ni.width().value() == 8 -> LLVM.LLVMInt8TypeInContext(context);
      case TyValueNumberInteger ni when ni.width().value() == 1 -> LLVM.LLVMInt1TypeInContext(context);
      case TyValueNumberInteger ni -> LLVM.LLVMIntTypeInContext(context, ni.width().value());

      case TyValueNumberPrecisioned np when np.width().value() == 128 -> LLVM.LLVMFP128TypeInContext(context);
      case TyValueNumberPrecisioned np when np.width().value() == 80 -> LLVM.LLVMX86FP80TypeInContext(context);
      case TyValueNumberPrecisioned np when np.width().value() == 64 -> LLVM.LLVMDoubleTypeInContext(context);
      case TyValueNumberPrecisioned np when np.width().value() == 32 -> LLVM.LLVMFloatTypeInContext(context);
      case TyValueNumberPrecisioned np when np.width().value() == 16 -> LLVM.LLVMHalfTypeInContext(context);

      // It is completely up to any code that uses these types to know that they are integers and need special care based on scale.
      case TyValueNumberScaled ni when ni.width().value() == 128 -> LLVM.LLVMInt128TypeInContext(context);
      case TyValueNumberScaled ni when ni.width().value() == 64 -> LLVM.LLVMInt64TypeInContext(context);
      case TyValueNumberScaled ni when ni.width().value() == 32 -> LLVM.LLVMInt32TypeInContext(context);
      case TyValueNumberScaled ni when ni.width().value() == 16 -> LLVM.LLVMInt16TypeInContext(context);
      case TyValueNumberScaled ni when ni.width().value() == 8 -> LLVM.LLVMInt8TypeInContext(context);
      case TyValueNumberScaled ni when ni.width().value() == 1 -> LLVM.LLVMInt1TypeInContext(context);

      case TyValueString s -> toLLVMType(context, new TyPointer<>(Ty.CHAR));

      case TyPointer p -> LLVM.LLVMPointerType(toLLVMType(context, p.inner()), getAddressSpace(p.addressSpace()));
      case TyValueArray a when a.size() >= 0 -> LLVM.LLVMArrayType2(toLLVMType(context, a.elementType()), a.size());
      case TyValueArray a -> LLVM.LLVMPointerType(toLLVMType(context, a.elementType()), 0);

      case TyValueBoolean b -> LLVM.LLVMInt1TypeInContext(context);

      case Ty.TyNamed n when n.intern() == Ty.VOID -> LLVM.LLVMVoidTypeInContext(context);

//      case  np when np.width() == 128 -> LLVM.LLVMTypeInContext(context);

      case TyFn fn -> {

        // TODO: BAD! This needs to be cached or set earlier in a centralized way. It is insane to recreate this very time it is called.
        final var fnParams = new LLVMTypeRef[fn.parameters().length];
        for (var i = 0; i < fn.parameters().length; i++) {
          final var param = fn.parameters()[i];
          fnParams[i] = MirToLLVMUtils.toLLVMType(context, param.ty());
        }

        yield LLVM.LLVMFunctionType(
          toLLVMType(context, fn.returnTy()),
          new PointerPointer<>(fnParams),
          fn.parameters().length,
          fn.vararg() ? 1 : 0
        );
      }
      default -> throw new IllegalArgumentException(STR."Do not know how to convert '\{ty.toShortString()}' (\{ty.getClass().getSimpleName()}) into an LLVM type");
    };
  }

  public static int getAddressSpace(TyPointerAddressSpace addressSpace) {

    if (addressSpace == null) {
      return 0;
    }

    return switch (addressSpace) {
      case CPU -> 0;
      case CUDA_GLOBAL -> 1;
      default -> throw new NotImplementedException("Have not implemented knowledge of any other address space");
    };
  }

  public record ArrayAndSize(LLVMValueRef ref, TyValueArray ty) {}

  public static ArrayAndSize createCharArray(MirToLLVMCtx mirToLlvmCtx, LLVMModuleRef module, String str) {

    final var bytes = (STR."\{str}\0").getBytes(StandardCharsets.UTF_8);
    final var charArray = new LLVMValueRef[bytes.length];
    final var elementTy = Ty.CHAR;
    final var charType = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, elementTy);
    for (int i = 0; i < bytes.length; i++) {
      charArray[i] = mirToLlvmCtx.getByte(bytes[i]);
    }

    final var strArray = LLVM.LLVMConstArray2(charType, new PointerPointer<>(charArray), bytes.length);

    final var arrayTy = new TyValueArray(elementTy, bytes.length);
    final var charArrayType = toLLVMType(mirToLlvmCtx, arrayTy);
    final var globalVar = LLVM.LLVMAddGlobal(module, charArrayType, "gs");
    LLVM.LLVMSetInitializer(globalVar, strArray);

    return new ArrayAndSize(globalVar, arrayTy);
  }

  public static void verifyModule(LLVMModuleRef module) {

    final var error = new BytePointer();
    try {

      if (LLVM.LLVMVerifyModule(module, LLVM.LLVMReturnStatusAction, error) != 0) {

        final var details = LLVM.LLVMPrintModuleToString(module).getString();

        final var errorMessage = error.getString();
        LLVM.LLVMDisposeMessage(error);

        throw map_error_message_to_exception(errorMessage, details);
      }

    } catch (Throwable t) {

      if (t instanceof RuntimeException re) {
        throw re;
      }

      throw new IllegalStateException(STR."Could not verify module, because: \{t}");
    } finally {
      error.deallocate();
    }
  }

  public static GenericLLVMException map_error_message_to_exception(String errorMessages, String details) {

    if (errorMessages != null && !errorMessages.isEmpty()) {

      if (containsAll(errorMessages, new String[]{"terminator", "found", "middle"})) {
        throw new UnreachableCodeLLVMException(errorMessages, details);
      }

    } else {
      errorMessages = "Unknown error";
    }

    return new GenericLLVMException(errorMessages, details);
  }

  private boolean containsAll(String haystack, String[] needles) {

    haystack = haystack.toLowerCase(Locale.ROOT);

    for (final var needle : needles) {
      if (!haystack.contains(needle)) {
        return false;
      }
    }

    return true;
  }
}
