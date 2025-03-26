package org.inf.llvm.lowering;

import java.util.concurrent.atomic.AtomicBoolean;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

public record LoweringResult(
  LLVMValueRef value,
  LLVMTypeRef type,
  /**
   * TODO: Remove this once a better way has been found -- maybe just skip the MIR -> LoweringResult completely? Chain of references should not break/be magic.
   */
  AtomicBoolean complete
) {

  public LoweringResult(LLVMValueRef value) {
    this(value, null, new AtomicBoolean(true));
  }

  public LoweringResult(LLVMValueRef value, LLVMTypeRef type) {
    this(value, type, new AtomicBoolean(true));
  }

  public LoweringResult(LLVMValueRef value, LLVMTypeRef type, AtomicBoolean complete) {
    this.value = value;
    this.type = type;
    this.complete = complete;

    if (this.value == null && this.type == null) {
      throw new IllegalArgumentException("Both value and type not allowed to be null");
    }
  }
}
