package org.inf.llvm.lowering;

import lombok.Value;
import org.inf.mir.Mir.InstrCreateFn;

import java.util.function.Consumer;

@Value
public class LLVMFunctionLoweringRequest {

  MirToLLVMCtx mirToLlvmCtx;
  InstrCreateFn fn;
  String name;
  Consumer<LLVMFunctionLoweringResult> callback;
}
