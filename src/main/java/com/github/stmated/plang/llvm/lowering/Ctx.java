package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.mir.model.MirInstr;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.ty.Ty;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

class Ctx {

  private final Map<String, LLVMValueRef> globalStringCache = new HashMap<>();
  private final Map<Byte, LLVMValueRef> cachedBytes = new HashMap<>();

  private final Map<MirInstr, LLVMValueRef> valueLookup = new HashMap<>();
  private final Map<MirNode, LLVMBasicBlockRef> blockLookup = new HashMap<>();

  private final Stack<LLVMValueRef> fnStack = new Stack<>();

  final LLVMContextRef context;
  final LLVMBuilderRef builder;

  public Ctx(LLVMContextRef context, LLVMBuilderRef builder) {
    this.context = context;
    this.builder = builder;
  }

  public LLVMValueRef getGlobalStringPtr(String str) {
    return globalStringCache.computeIfAbsent(str, s -> LLVM.LLVMBuildGlobalStringPtr(builder, s, "str"));
  }

  public LLVMValueRef getByte(byte bite) {

    // Q: Is this worth it? Try with and without.
    final var ty = Ty.CHAR;
    final var charType = MirToLLVMUtils.toLLVMType(context, ty);
    return cachedBytes.computeIfAbsent(bite, b -> LLVM.LLVMConstInt(charType, b, ty.signed() ? 1 : 0));
  }

  public LLVMValueRef resolve(MirInstr miri) {
    return Objects.requireNonNull(
      this.valueLookup.get(miri),
      "Every instruction that we lookup must be a handled predecessor of when we need to resolve it"
    );
  }

  public LLVMValueRef resolveIfAvailable(MirInstr miri) {
    return this.valueLookup.get(miri);
  }

  public void register(MirInstr miri, LLVMValueRef ref) {
    if (this.valueLookup.containsKey(miri)) {
      throw new IllegalArgumentException(STR."Not allowed to register a value ref for '\{miri}' twice!");
    }

    this.valueLookup.put(
      Objects.requireNonNull(miri, "Must give an instruction to register the llvm value ref to"),
      Objects.requireNonNull(ref, "LLVMValueRef you register must not be null")
    );
  }

  public void registerBlock(MirNode node, LLVMBasicBlockRef blockRef) {
    blockLookup.put(node, blockRef);
  }

  public LLVMBasicBlockRef resolveBlock(MirNode node) {
    return Objects.requireNonNull(blockLookup.get(node), STR."Node '\{node}' was not found in first pass of CFG");
  }

  public void enterFunction(LLVMValueRef fnRef, Runnable runnable) {

    try {
      fnStack.push(fnRef);
      runnable.run();
    } finally {
      final var popped = fnStack.pop();
      if (fnRef != popped) {
        throw new IllegalStateException(STR."Popped the wrong fn, expected '\{fnRef}' got '\{popped}'");
      }
    }
  }

  public LLVMValueRef getFunction() {
    return fnStack.peek();
  }
}
