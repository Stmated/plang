package org.inf.llvm.lowering;

import lombok.Value;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;

@Value
public class LLVMFunctionLoweringResult {

  LLVMModuleRef module;
}
