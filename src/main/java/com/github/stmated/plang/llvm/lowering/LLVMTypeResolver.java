package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyFn;
import com.github.stmated.plang.ty.TyOpaque;
import com.github.stmated.plang.ty.TyPointer;
import com.github.stmated.plang.ty.TyPointerAddressSpace;
import com.github.stmated.plang.ty.TyStruct;
import com.github.stmated.plang.ty.TyValueArray;
import com.github.stmated.plang.ty.TyValueBoolean;
import com.github.stmated.plang.ty.TyValueNumberInteger;
import com.github.stmated.plang.ty.TyValueNumberPrecisioned;
import com.github.stmated.plang.ty.TyValueNumberScaled;
import com.github.stmated.plang.ty.TyValueString;
import lombok.RequiredArgsConstructor;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.global.LLVM;

@RequiredArgsConstructor
public class LLVMTypeResolver {

  private final LLVMContextRef context;
  private final Cache<Ty, LLVMTypeRef> cache;

  public LLVMTypeRef resolve(Ty ty) {

    final var cached = cache.get(ty);
    if (cached != null) {
      return cached;
    }

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

      case TyValueString s -> resolve(new TyPointer<>(Ty.CHAR));

      case TyPointer p -> LLVM.LLVMPointerType(resolve( p.inner()), getAddressSpace(p.addressSpace()));
      case TyValueArray a when a.size() != null && a.size() >= 0 -> LLVM.LLVMArrayType2(resolve( a.elementType()), a.size());
      case TyValueArray a -> LLVM.LLVMPointerType(resolve( a.elementType()), 0);

      case TyValueBoolean b -> LLVM.LLVMInt1TypeInContext(context);

      case Ty.TyNamed n when n.intern() == Ty.VOID -> LLVM.LLVMVoidTypeInContext(context);

      case TyStruct s -> {

        // TODO: Need to keep track of the ty and type, so we get back the same type. Up to caller?

        final var types = new PointerPointer<>(s.fields().length);
        for (var i = 0; i < s.fields().length; i++) {
          types.put(i, resolve( s.fields()[i].ty()));
        }

        final var type = LLVM.LLVMStructTypeInContext(context, types, s.fields().length, 0);
        cache.put(s, type);

        yield type;
      }

      case TyFn fn -> {

        // TODO: BAD! This needs to be cached or set earlier in a centralized way. It is insane to recreate this very time it is called.
        final var fnParams = new LLVMTypeRef[fn.parameters().length];
        for (var i = 0; i < fn.parameters().length; i++) {
          final var param = fn.parameters()[i];
          fnParams[i] = resolve( param.ty());
        }

        yield LLVM.LLVMFunctionType(
          resolve( fn.returnTy()),
          new PointerPointer<>(fnParams),
          fn.parameters().length,
          fn.vararg() ? 1 : 0
        );
      }
      case TyOpaque opaque -> {

        final var type = LLVM.LLVMStructCreateNamed(context, "opaque");
        cache.put(opaque, type);

        yield type;
      }
      default -> throw new IllegalArgumentException("Do not know how to convert '%s' (%s) into an LLVM type".formatted(ty.toShortString(), ty.getClass().getSimpleName()));
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
}
