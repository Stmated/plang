package org.inf.llvm.lowering;

import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.inf.ty.TyValueArray;

public record ArrayAndSize(LLVMValueRef ref, TyValueArray ty) {

}
