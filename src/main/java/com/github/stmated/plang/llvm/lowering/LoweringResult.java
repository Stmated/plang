package com.github.stmated.plang.llvm.lowering;

import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

public record LoweringResult(
  LLVMValueRef value,
  LLVMTypeRef type
) {

  public LoweringResult(LLVMValueRef value) {
    this(value, null);
  }

  public LoweringResult(LLVMValueRef value, LLVMTypeRef type) {
    this.value = value;
    this.type = type;

    if (this.value == null && this.type == null) {
      throw new IllegalArgumentException("Both value and type not allowed to be null");
    }
  }
}
