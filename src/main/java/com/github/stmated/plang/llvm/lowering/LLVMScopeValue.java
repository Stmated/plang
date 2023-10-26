package com.github.stmated.plang.llvm.lowering;

import java.util.concurrent.atomic.AtomicReference;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

record LLVMScopeValue(LLVMValueRef ref, AtomicReference<LLVMValueRef> loaded) {

  LLVMScopeValue(LLVMValueRef ref) {
    this(ref, new AtomicReference<>());
  }

  LLVMScopeValue(LLVMValueRef ref, AtomicReference<LLVMValueRef> loaded) {
    this.ref = ref;
    this.loaded = loaded;
  }
}
