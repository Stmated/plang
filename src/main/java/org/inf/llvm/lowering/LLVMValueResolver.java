package org.inf.llvm.lowering;

import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;
import org.inf.ty.Ty;
import org.inf.ty.TyValueArray;

import java.nio.charset.StandardCharsets;

public class LLVMValueResolver {

  private final Cache<Byte, LLVMValueRef> bytes = new Cache<>();

  private final LLVMTypeResolver typeResolver;
  private final LLVMModuleRef moduleRef;

  public LLVMValueResolver(LLVMTypeResolver typeResolver, LLVMModuleRef moduleRef) {
    this.typeResolver = typeResolver;
    this.moduleRef = moduleRef;
  }

  public LLVMValueRef getByte(byte bite) {

    // Q: Is this worth it? Try with and without.
    final var ty = Ty.CHAR;
    final var charType = typeResolver.resolve(ty);
    return bytes.getOrCompute(bite, () -> LLVM.LLVMConstInt(charType, bite, ty.signed() ? 1 : 0));
  }

  public ArrayAndSize createCharArray(String str) {

    final var bytes = (str + "\u0000").getBytes(StandardCharsets.UTF_8);
    final var charArray = new LLVMValueRef[bytes.length];
    final var elementTy = Ty.CHAR;
    final var charType = typeResolver.resolve(elementTy);
    for (int i = 0; i < bytes.length; i++) {
      charArray[i] = this.getByte(bytes[i]);
    }

    final var strArray = LLVM.LLVMConstArray2(charType, new PointerPointer<>(charArray), bytes.length);

    final var arrayTy = new TyValueArray(elementTy, bytes.length);
    final var charArrayType = typeResolver.resolve(arrayTy);
    final var globalVar = LLVM.LLVMAddGlobal(moduleRef, charArrayType, "gs");
    LLVM.LLVMSetInitializer(globalVar, strArray);

    return new ArrayAndSize(globalVar, arrayTy);
  }
}
