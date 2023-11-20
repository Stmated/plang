package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.mir.model.MirInstrCreateFn;
import com.github.stmated.plang.mir.model.MirInstr;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.util.Pair;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMContextRef;
import org.bytedeco.llvm.LLVM.LLVMOrcThreadSafeContextRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

class MirToLLVMCtx {

  private final Map<String, LLVMValueRef> globalStringCache = new HashMap<>();
  private final Map<Byte, LLVMValueRef> cachedBytes = new HashMap<>();

  private final Map<MirInstr, LoweringResult> valueLookup = new HashMap<>();
  /**
   * TODO: Could perhaps one day be removed in favor of keeping the type reference being sent along the chain?
   */
  private final Map<Ty, LLVMTypeRef> typeLookup = new HashMap<>();
  private final Map<MirNode, LLVMBasicBlockRef> blockLookup = new HashMap<>();

  private final Stack<Pair<MirInstrCreateFn, LLVMValueRef>> fnStack = new Stack<>();

  final LLVMOrcThreadSafeContextRef threadContext;
  final LLVMContextRef context;
  final LLVMBuilderRef builder;

  public MirToLLVMCtx(LLVMOrcThreadSafeContextRef threadContext, LLVMContextRef context, LLVMBuilderRef builder) {
    this.threadContext = threadContext;
    this.context = context;
    this.builder = builder;
  }

  public LLVMValueRef getGlobalStringPtr(String str) {
    return globalStringCache.computeIfAbsent(str, s -> LLVM.LLVMBuildGlobalStringPtr(builder, s, "str"));
  }

  public LLVMValueRef getByte(byte bite) {

    // Q: Is this worth it? Try with and without.
    final var ty = Ty.CHAR;
    final var charType = MirToLLVMUtils.toLLVMType(this, ty);
    return cachedBytes.computeIfAbsent(bite, b -> LLVM.LLVMConstInt(charType, b, ty.signed() ? 1 : 0));
  }

  public LoweringResult resolve(MirInstr miri) {
    return Objects.requireNonNull(
      this.valueLookup.get(miri),
      "Every instruction that we lookup must be a handled predecessor of when we need to resolve it"
    );
  }

  public LoweringResult resolveIfAvailable(MirInstr miri) {
    return this.valueLookup.get(miri);
  }

  public void register(MirInstr miri, LoweringResult ref) {
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

  public void enterFunction(Pair<MirInstrCreateFn, LLVMValueRef> fnRef, Runnable runnable) {

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

  public Pair<MirInstrCreateFn, LLVMValueRef> getFunction() {
    return fnStack.peek();
  }

  public Iterator<Pair<MirInstrCreateFn, LLVMValueRef>> getFunctionIterator() {
    return fnStack.reversed().iterator();
  }

  public void registerType(Ty ty, LLVMTypeRef typeRef) {
    typeLookup.put(ty, typeRef);
  }

  public LLVMTypeRef resolveType(Ty ty) {
    return typeLookup.get(ty);
  }
}
