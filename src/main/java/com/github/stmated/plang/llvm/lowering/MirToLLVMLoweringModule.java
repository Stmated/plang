package com.github.stmated.plang.llvm.lowering;

import static org.bytedeco.llvm.global.LLVM.LLVMAddIncoming;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildCondBr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildPhi;

import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.exceptions.UnexpectedExpressionException;
import com.github.stmated.plang.mir.model.MirCall;
import com.github.stmated.plang.mir.model.MirFn;
import com.github.stmated.plang.mir.model.MirInstr;
import com.github.stmated.plang.mir.model.MirInstrBinaryOperation;
import com.github.stmated.plang.mir.model.MirInstrConditionalJump;
import com.github.stmated.plang.mir.model.MirInstrCreateLiteral;
import com.github.stmated.plang.mir.model.MirInstrJump;
import com.github.stmated.plang.mir.model.MirInstrPhi;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.mir.model.MirReturn;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyPointer;
import com.github.stmated.plang.ty.TyValueArray;
import com.github.stmated.plang.ty.TyValueBoolean;
import com.github.stmated.plang.ty.TyValueNumberInteger;
import com.github.stmated.plang.ty.TyValueNumberPrecisioned;
import com.github.stmated.plang.ty.TyValueString;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.LongPointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

/**
 * One LLVM module in our case is one MIR function. It will build the CFG of this only.
 * <p>
 * It is then up to the main {@link MirToLLVMLowering} to mend all the different functions together into one bigger program.
 */
@Slf4j
class MirToLLVMLoweringModule implements AutoCloseable {

  private final LLVMModuleRef module;
//  private final LLVMBuilderRef builder;

  private final Ctx ctx;

  /**
   * TODO: This should not be filled on-demand, it should be filled as a pass through all instructions -- so it is known at all times.
   *        We then of course need to change this Map to be
   */
  private final Map<LLVMValueRef, Ty> overridingTypes = new HashMap<>();

  public MirToLLVMLoweringModule(Ctx ctx, String name) {
    this.ctx = ctx;
    this.module = LLVM.LLVMModuleCreateWithNameInContext(name, ctx.context);

    // NOTE: Change this according to the actual target!
    LLVM.LLVMSetTarget(module, "arm64-apple-macosx14.0.0");
    MirToLLVMUtils.verifyModule(module);
  }

  @Override
  public void close() {
    LLVM.LLVMDisposeModule(module);
  }

  private ExternalFn createFnDeclaration(MirFn mirFn) {

    final var fnReturnType = MirToLLVMUtils.toLLVMType(ctx.context, mirFn.returnType());
    final var fnParams = new LLVMTypeRef[mirFn.parameters().length];
    for (var i = 0; i < mirFn.parameters().length; i++) {

      final var mirParam = mirFn.parameters()[i];
      final var mirParamType = mirParam.type();

      fnParams[i] = MirToLLVMUtils.toLLVMType(ctx.context, mirParamType);
    }

    final var vararg = mirFn.vararg() ? 1 : 0;
    final var fnType = LLVM.LLVMFunctionType(fnReturnType, new PointerPointer<>(fnParams), fnParams.length, vararg);
    final var fn = LLVM.LLVMAddFunction(module, mirFn.name(), fnType);

    for (var i = 0; i < mirFn.parameters().length; i++) {

      final var mirParam = mirFn.parameters()[i];
      if (mirParam.name() != null) {

        final var param = LLVM.LLVMGetParam(fn, i);
        LLVM.LLVMSetValueName(param, mirParam.name());
      }
    }

    return new ExternalFn(fn, fnType, fnParams);
  }

  public ModuleResult lower(MirFn mirFn) {

    final var fn = this.createFnDeclaration(mirFn);

    firstPassTraverseNodes(mirFn.entry(), fn.fn());

    ctx.enterFunction(fn.fn(), () -> secondPassBuildNodes(mirFn.entry()));

    MirToLLVMUtils.verifyModule(module);

    final var function = (mirFn.name().equals("main"))
      // If the function is called "main" then we will just take the word of it and let it be the program entrypoint.
      ? null
      // But if it is not "main", then we will give back the LLVM call info for this function to the caller.
      // It is then up to the caller to decide what to do with this information.
      : new LLVMFunctionCallInfo(fn.fnType(), fn.fn(), new PointerPointer<>(fn.params()), fn.params().length, mirFn.name(), ctx.resolveBlock(mirFn.entry()));

    return new ModuleResult(
      module,
      function,
      () -> {
      }
    );
  }

  private void forEachNode(MirNode root, Consumer<MirNode> consumer) {

    final var visited = new ArrayList<MirNode>();
    final var remaining = new ArrayDeque<MirNode>();
    remaining.add(root);

    while (!remaining.isEmpty()) {

      final var node = remaining.pop();
      if (visited.contains(node)) {
        continue;
      }

      visited.add(node);
      consumer.accept(node);

      remaining.addAll(node.successors());
    }
  }

  private void firstPassTraverseNodes(MirNode root, LLVMValueRef fnRef) {

    forEachNode(root, node -> {

      final var blockName = (root == node) ? "entry" : node.name();
      final var block = LLVM.LLVMAppendBasicBlockInContext(ctx.context, fnRef, blockName);
      ctx.registerBlock(node, block);
    });
  }

  private void secondPassBuildNodes(MirNode root) {
    forEachNode(root, this::move_to_and_lower_node);
  }

  private LLVMValueRef move_to_and_lower_node(MirNode node) {

    final var nodeBlock = ctx.resolveBlock(node);
    LLVM.LLVMPositionBuilderAtEnd(ctx.builder, nodeBlock);

    LLVMValueRef last = null;
    for (final var instruction : node.instructions()) {
      last = lower_instruction(instruction);
    }

    return last;
  }

  private LLVMValueRef lower_instruction(MirInstr miri) {

    final var ref = ctx.resolveIfAvailable(miri);
    if (ref != null) {
      return ref;
    }

    return lower_instruction_inner(miri);
  }

  private LLVMValueRef lower_instruction_inner(MirInstr miri) {

    final LLVMValueRef valueRef = switch (miri) {
      case MirInstrCreateLiteral it -> lower_literal(it);
      case MirInstrBinaryOperation it -> lower_binary_operation(it);
      case MirReturn it -> lower_return(it);
      case MirInstrConditionalJump it -> lower_conditional_jump(it);
      case MirInstrJump it -> lower_jump(it);
      case MirInstrPhi it -> lower_phi(it);
      case MirCall it -> lower_call(it);
      default -> throw new UnexpectedExpressionException(miri);
    };

    // All instructions will always result in a valueRef, even if it is a void value.
    ctx.register(miri, valueRef);

    return valueRef;
  }

  private record ExternalFn(LLVMValueRef fn, LLVMTypeRef fnType, LLVMTypeRef[] params) {

  }

  private Map<String, ExternalFn> externalFunctionMap = new HashMap<>();

  private LLVMValueRef lower_call(MirCall mir) {

    final var functionName = mir.target().name();

    final var fn = Objects.requireNonNull(
      externalFunctionMap.computeIfAbsent(functionName, _ -> createFnDeclaration(mir.target())),
      () -> STR."Unknown function '\{functionName}'"
    );

    final var llvmArgs = new LLVMValueRef[mir.arguments().length];
    for (var i = 0; i < mir.arguments().length; i++) {

      final var arg = mir.arguments()[i];
      final var ref = lower_instruction(arg.instruction());

      // There can be less params than args if the function uses varargs.
      if (i < fn.params().length) {

        final var mirParam = mir.target().parameters()[i];
        llvmArgs[i] = convert(ref, arg.instruction().ty(), mirParam.type());
      } else {
        llvmArgs[i] = ref;
      }
    }

    final var pp = new PointerPointer<>(llvmArgs);
    return LLVM.LLVMBuildCall2(ctx.builder, fn.fnType(), fn.fn(), pp, llvmArgs.length, STR."call \{functionName}");
  }

  private LLVMValueRef convert(LLVMValueRef ref, Ty given, Ty expected) {

    given = overridingTypes.getOrDefault(ref, given);

    final var lowGiven = getLowTy(given);
    final var lowExpected = getLowTy(expected);

    if (lowGiven instanceof TyValueArray va) {
      if (lowExpected instanceof TyPointer) {

        // global variables are actually treated as single-item arrays.
        // So index 0 of the global item, then index 0 of that array.

        final var indices = new PointerPointer<>(2);
        indices.put(0, LLVM.LLVMConstInt(MirToLLVMUtils.toLLVMType(ctx, Ty.INTEGER), 0, 0));
        indices.put(1, LLVM.LLVMConstInt(MirToLLVMUtils.toLLVMType(ctx, Ty.INTEGER), 0, 0));

        final var targetType = va.elementType();
        final var gepType = MirToLLVMUtils.toLLVMType(ctx, targetType);

        return LLVM.LLVMBuildGEP2(ctx.builder, gepType, ref, indices, 2, "gep");
      }
    } else if (lowGiven instanceof TyPointer gp) {
      if (!(lowExpected instanceof TyPointer)) {

        final var type = MirToLLVMUtils.toLLVMType(ctx, gp.inner());
        return LLVM.LLVMBuildLoad2(ctx.builder, type, ref, "loaded");
      }
    }

    return ref;
  }

  private static final TyPointer STR_CHAR_POINTER = new TyPointer(Ty.CHAR);

  /**
   * TODO: Create a new type object called LowTy that has things like: "isGlobal" "original" and "low"
   *        Then use it everywhere in this lowering -- so we can be sure we're working with a lowered type (but access the original)
   */
  private Ty getLowTy(Ty ty) {

    if (ty instanceof TyValueString) {
      return STR_CHAR_POINTER;
    }

    return ty;
  }

  private LLVMValueRef lower_phi(MirInstrPhi mir) {

    Ty overridingType = null;
    final var phiValues = new PointerPointer<>(mir.operands().length);
    for (var i = 0; i < mir.operands().length; i++) {

      final var valueRef = lower_instruction(mir.operands()[i]);
      if (overridingType == null) {
        overridingType = overridingTypes.get(valueRef);
      }

      phiValues.put(i, valueRef);
    }

    final var phiBlocks = new PointerPointer<>(mir.from().length);
    for (var i = 0; i < mir.from().length; i++) {

      final var blockRef = ctx.resolveBlock(mir.from()[i]);
      phiBlocks.put(i, blockRef);
    }

    final var actualTy = Objects.requireNonNullElse(overridingType, mir.ty());
    final var phiValueType = MirToLLVMUtils.toLLVMType(ctx, actualTy);
    final var phi = LLVMBuildPhi(ctx.builder, phiValueType, "result");

    LLVMAddIncoming(phi, phiValues, phiBlocks, mir.from().length);

    return phi;
  }

  private LLVMValueRef lower_jump(MirInstrJump it) {

    // TODO: This forces blocks/functions to have been visited before they are used.
    //        Need to add code that does two passes (block discovery first); or use placeholders which are resolved later.

    final var known = ctx.resolveBlock(it.node());
    return LLVM.LLVMBuildBr(ctx.builder, known);
  }

  private LLVMValueRef lower_conditional_jump(MirInstrConditionalJump it) {

    final var instr_predicate = lower_instruction(it.predicate());

    final var passBlock = ctx.resolveBlock(it.pass());
    final var failBlock = ctx.resolveBlock(it.fail());

    return LLVMBuildCondBr(ctx.builder, instr_predicate, passBlock, failBlock);
  }

  private LLVMValueRef lower_return(MirReturn mir) {

    // We do not do any casts or convert here.
    // It is up to the THIR and MIR to add compatibility instructions.

    var ref = lower_instruction(mir.instr());
    final var actualType = LLVM.LLVMTypeOf(ref);

    if (LLVM.LLVMGetTypeKind(actualType) == LLVM.LLVMPointerTypeKind) {

      // This is the type we actually want out from the ref, when it is a pointer.
      final var derefType = MirToLLVMUtils.toLLVMType(ctx, mir.ty());

      // Load the value into our registers, and that is what we will return.
      ref = LLVM.LLVMBuildLoad2(ctx.builder, derefType, ref, getLabel(mir.instr(), "value"));
    }

    return LLVM.LLVMBuildRet(ctx.builder, ref);
  }

  private LLVMValueRef lower_literal(MirInstrCreateLiteral literal) {

    return switch (literal.ty()) {
      case TyValueString str -> lower_literal_string(literal.content(), str);
      case TyValueNumberInteger ni -> lower_literal_number_integer(literal, ni);
      case TyValueNumberPrecisioned np -> lower_literal_number_precisioned(literal.content(), np);
      case TyValueBoolean b -> lower_literal_boolean(literal.content(), b);
      default -> throw new UnexpectedExpressionException(literal);
    };
  }

  private LLVMValueRef lower_literal_boolean(String strValue, TyValueBoolean b) {
    final var value = Boolean.parseBoolean(strValue);
    return LLVM.LLVMConstInt(MirToLLVMUtils.toLLVMType(ctx, b), value ? 1 : 0, 0);
  }

  private final Map<String, LLVMValueRef> globalStringCache = new HashMap<>();

  private LLVMValueRef getGlobalStringPtr(String str) {
    return globalStringCache.computeIfAbsent(str, s -> LLVM.LLVMBuildGlobalStringPtr(ctx.builder, s, "str"));
  }

  private LLVMValueRef lower_literal_string(String content, TyValueString str) {

    // TODO: Like in Rust, should we separate the different kinds of strings into different types? Global, char array, others?

//    final var globalString = getGlobalStringPtr(content);
//    overridingTypes.put(globalString, new TyPointer(Ty.CHAR));
//
//    return globalString;

    final var array = MirToLLVMUtils.createCharArray(ctx, module, content);
    overridingTypes.put(array.ref(), array.ty());

    return array.ref();
  }

  private LLVMValueRef lower_literal_number_integer(MirInstrCreateLiteral literal, TyValueNumberInteger ty) {

    // TODO: Wrong? Or can it handle octal, hex and binary?

    final var content = literal.content();
    final var v = Integer.parseInt(content, ty.radix());
    final var typeRef = MirToLLVMUtils.toLLVMType(ctx, ty);
    final var constant = LLVM.LLVMConstInt(typeRef, v, ty.signed() ? 1 : 0);

    if (ty.isConstant()) {
      return constant;
    } else {

      final var allocation = LLVM.LLVMBuildAlloca(ctx.builder, typeRef, ty.toShortString());
      LLVM.LLVMBuildStore(ctx.builder, constant, allocation);

      // Override the type to be a pointer of the type.
      overridingTypes.put(allocation, new TyPointer(ty));

      return allocation;
    }
  }

  private LLVMValueRef lower_literal_number_precisioned(String content, TyValueNumberPrecisioned np) {
    throw new NotImplementedException("Implement precisioned numbers");
  }

  private String getLabel(MirInstr instruction, String fallback) {

    if (instruction.name() != null) {

      final var label = instruction.name().label();
      if (label != null) {
        return label;
      }
    }

    return fallback;
  }

  private LLVMValueRef lower_binary_operation(MirInstrBinaryOperation mir) {

    var lhs = lower_instruction(mir.lhs());
    var rhs = lower_instruction(mir.rhs());

    final var lhsActualType = LLVM.LLVMTypeOf(lhs);
    final var rhsActualType = LLVM.LLVMTypeOf(rhs);

    final var lhsTy = mir.lhs().ty();
    final var rhsTy = mir.rhs().ty();

    // This is the type we want the binary operation to be for
    final var lhsType = MirToLLVMUtils.toLLVMType(ctx, lhsTy);
    final var rhsType = MirToLLVMUtils.toLLVMType(ctx, rhsTy);

    if (LLVM.LLVMGetTypeKind(lhsActualType) == LLVM.LLVMPointerTypeKind) {
      lhs = LLVM.LLVMBuildLoad2(ctx.builder, lhsType, lhs, getLabel(mir.lhs(), "lhs"));
    }

    if (LLVM.LLVMGetTypeKind(rhsActualType) == LLVM.LLVMPointerTypeKind) {
      rhs = LLVM.LLVMBuildLoad2(ctx.builder, rhsType, rhs, getLabel(mir.rhs(), "rhs"));
    }

    return switch (mir.kind()) {
      case ADD -> LLVM.LLVMBuildAdd(ctx.builder, lhs, rhs, STR."\{mir.lhs()} add \{mir.rhs()}");
      case SUBTRACT -> LLVM.LLVMBuildSub(ctx.builder, lhs, rhs, STR."\{mir.lhs()} sub \{mir.rhs()}");
      case MULTIPLY -> LLVM.LLVMBuildMul(ctx.builder, lhs, rhs, STR."\{mir.lhs()} mul \{mir.rhs()}");
      case DIVIDE -> {

        final var lhsKind = LLVM.LLVMGetTypeKind(lhsType);
        final var rhsKind = LLVM.LLVMGetTypeKind(rhsType);

        if (lhsKind == LLVM.LLVMIntegerTypeKind && rhsKind == LLVM.LLVMIntegerTypeKind) {

          // TODO: Need to have information in the HIR for if these values are signed or unsigned.
          yield LLVM.LLVMBuildSDiv(ctx.builder, lhs, rhs, "lhs / rhs");
        }

        // TODO: This is wrong -- if it is two float16 it should not be converted into a float32
        LLVMValueRef correctLhs;
        LLVMValueRef correctRhs;
        if (lhsKind == LLVM.LLVMDoubleTypeKind || rhsKind == LLVM.LLVMDoubleTypeKind) {
          correctLhs = getValueAsFloat(lhs, MirToLLVMUtils.toLLVMType(ctx, Ty.FLOAT64), lhsKind, mir.lhs());
          correctRhs = getValueAsFloat(rhs, MirToLLVMUtils.toLLVMType(ctx, Ty.FLOAT64), rhsKind, mir.rhs());
        } else if (lhsKind == LLVM.LLVMFloatTypeKind || rhsKind == LLVM.LLVMFloatTypeKind) {
          correctLhs = getValueAsFloat(lhs, MirToLLVMUtils.toLLVMType(ctx, Ty.FLOAT), lhsKind, mir.lhs());
          correctRhs = getValueAsFloat(rhs, MirToLLVMUtils.toLLVMType(ctx, Ty.FLOAT), rhsKind, mir.rhs());
        } else if (lhsKind == LLVM.LLVMHalfTypeKind || rhsKind == LLVM.LLVMHalfTypeKind) {
          correctLhs = getValueAsFloat(lhs, MirToLLVMUtils.toLLVMType(ctx, Ty.FLOAT16), lhsKind, mir.lhs());
          correctRhs = getValueAsFloat(rhs, MirToLLVMUtils.toLLVMType(ctx, Ty.FLOAT16), rhsKind, mir.rhs());
        } else {
          throw new NotImplementedException(STR."Cannot divide values of types '\{mir.lhs()}' and '\{mir.rhs()}'");
        }

        yield LLVM.LLVMBuildFDiv(ctx.builder, correctLhs, correctRhs, STR."\{mir.lhs()} / \{mir.rhs()}");
      }
      case EQUALS -> LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMIntEQ, lhs, rhs, STR."\{mir.lhs()} eq \{mir.rhs()}");
      case LT -> LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMIntSLT, lhs, rhs, STR."\{mir.lhs()} lt \{mir.rhs()}");
      case LTE -> LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMIntSLE, lhs, rhs, STR."\{mir.lhs()} lte \{mir.rhs()}");
      case GT -> LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMIntSGT, lhs, rhs, STR."\{mir.lhs()} gt \{mir.rhs()}");
      case GTE -> LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMIntSGE, lhs, rhs, STR."\{mir.lhs()} gte \{mir.rhs()}");
      default -> throw new IllegalArgumentException(STR."Unknown binary operation kind '\{mir.kind()}'");
    };
  }

  private LLVMValueRef getValueAsFloat(LLVMValueRef v, LLVMTypeRef targetType, int lhsKind, MirInstr miri) {

    if (lhsKind == LLVM.LLVMFloatTypeKind) {
      return v;
    } else if (lhsKind == LLVM.LLVMHalfTypeKind) {
      return LLVM.LLVMBuildFPExt(ctx.builder, v, targetType, "float16ToFloat32");
    } else if (lhsKind == LLVM.LLVMDoubleTypeKind) {
      return LLVM.LLVMBuildFPTrunc(ctx.builder, v, targetType, "doubleToFloat32");
    } else if (lhsKind == LLVM.LLVMIntegerTypeKind) {
      return LLVM.LLVMBuildSIToFP(ctx.builder, v, targetType, "intToFloat32");
    } else {
      throw new NotImplementedException(STR."Cannot convert '\{miri}' into a float");
    }
  }
}
