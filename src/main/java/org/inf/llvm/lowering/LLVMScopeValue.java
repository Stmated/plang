package org.inf.llvm.lowering;

import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.concurrent.atomic.AtomicReference;

record LLVMScopeValue(LLVMValueRef ref, AtomicReference<LLVMValueRef> loaded) {

  LLVMScopeValue(LLVMValueRef ref) {
    this(ref, new AtomicReference<>());
  }

  LLVMScopeValue(LLVMValueRef ref, AtomicReference<LLVMValueRef> loaded) {
    this.ref = ref;
    this.loaded = loaded;
  }
}
