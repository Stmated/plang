package com.github.stmated.plang.llvm.lowering;

import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.LLVM.LLVMOrcThreadSafeContextRef;

public record ModuleResult(

  LLVMModuleRef module,
  LLVMContextRef context,
  LLVMFunctionCallInfo fn,
  Runnable disposeCallback,
  LLVMOrcThreadSafeContextRef threadContext
) {

}
