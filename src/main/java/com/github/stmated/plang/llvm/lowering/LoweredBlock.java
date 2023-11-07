package com.github.stmated.plang.llvm.lowering;

import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

record LoweredBlock(LLVMBasicBlockRef blockRef, LLVMValueRef valueRef) {

}
