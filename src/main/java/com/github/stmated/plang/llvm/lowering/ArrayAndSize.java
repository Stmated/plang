package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.ty.TyValueArray;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

public record ArrayAndSize(LLVMValueRef ref, TyValueArray ty) {

}
