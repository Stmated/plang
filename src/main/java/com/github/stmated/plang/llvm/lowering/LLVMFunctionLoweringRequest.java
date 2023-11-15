package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.mir.model.MirInstrCreateFn;
import java.util.function.Consumer;
import lombok.Value;

@Value
public class LLVMFunctionLoweringRequest {

  MirToLLVMCtx mirToLlvmCtx;
  MirInstrCreateFn fn;
  String name;
  Consumer<LLVMFunctionLoweringResult> callback;
}
