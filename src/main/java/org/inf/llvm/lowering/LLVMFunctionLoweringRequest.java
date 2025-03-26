package org.inf.llvm.lowering;

import org.inf.mir.Mir.InstrCreateFn;
import java.util.function.Consumer;
import lombok.Value;

@Value
public class LLVMFunctionLoweringRequest {

  MirToLLVMCtx mirToLlvmCtx;
  InstrCreateFn fn;
  String name;
  Consumer<LLVMFunctionLoweringResult> callback;
}
