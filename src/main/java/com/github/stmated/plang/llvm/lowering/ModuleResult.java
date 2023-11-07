package com.github.stmated.plang.llvm.lowering;

import org.bytedeco.llvm.LLVM.LLVMModuleRef;

public record ModuleResult(

  LLVMModuleRef module,
  LLVMFunctionCallInfo fn,
  Runnable disposeCallback
) {

}
