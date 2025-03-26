package org.inf.llvm.lowering;

import lombok.Value;
import org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.LLVM.LLVMOrcThreadSafeContextRef;

@Value
public class LLVMCtx {

  LLVMOrcThreadSafeContextRef threadContext;
  LLVMContextRef context;
  LLVMModuleRef module;
  LLVMBuilderRef builder;

  public LLVMCtx(
    LLVMOrcThreadSafeContextRef threadContext,
    LLVMContextRef context,
    LLVMModuleRef module,
    LLVMBuilderRef builder
  ) {
    this.threadContext = threadContext;
    this.context = context;
    this.module = module;
    this.builder = builder;
  }
}
