package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.mir.model.MirFn;
import java.util.function.Consumer;
import lombok.Value;

@Value
public class LLVMFunctionLoweringRequest {

  Ctx ctx;
  MirFn fn;
  Consumer<LLVMFunctionLoweringResult> callback;
}
