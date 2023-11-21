package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.mir.Mir.InstrCreateFn;
import java.util.function.Consumer;
import lombok.Value;

@Value
public class LLVMFunctionLoweringRequest {

  MirToLLVMCtx mirToLlvmCtx;
  InstrCreateFn fn;
  String name;
  Consumer<LLVMFunctionLoweringResult> callback;
}
