package org.inf.llvm.lowering;

import org.inf.ty.TyValueArray;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

public record ArrayAndSize(LLVMValueRef ref, TyValueArray ty) {

}
