package com.github.stmated.plang.llvm.lowering;

import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

public record LLVMFunctionCallInfo(
  LLVMTypeRef fnType,
  LLVMValueRef fn,
  PointerPointer<LLVMTypeRef> arguments,
  int argumentCount,
  String name,
  LLVMBasicBlockRef block
) {

}
