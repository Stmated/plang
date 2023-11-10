package com.github.stmated.plang.llvm.lowering;

import static org.bytedeco.llvm.global.LLVM.LLVMAddIncoming;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildCondBr;
import static org.bytedeco.llvm.global.LLVM.LLVMBuildPhi;

import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.exceptions.UnexpectedExpressionException;
import com.github.stmated.plang.llvm.util.LLVMTys;
import com.github.stmated.plang.mir.model.MirCall;
import com.github.stmated.plang.mir.model.MirFn;
import com.github.stmated.plang.mir.model.MirInstr;
import com.github.stmated.plang.mir.model.MirInstrBinaryOperation;
import com.github.stmated.plang.mir.model.MirInstrConditionalJump;
import com.github.stmated.plang.mir.model.MirInstrCreateLiteral;
import com.github.stmated.plang.mir.model.MirInstrGetGlobal;
import com.github.stmated.plang.mir.model.MirInstrJump;
import com.github.stmated.plang.mir.model.MirInstrPhi;
import com.github.stmated.plang.mir.model.MirInstrStore;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.mir.model.MirReturn;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyPointer;
import com.github.stmated.plang.ty.TyValueArray;
import com.github.stmated.plang.ty.TyValueBoolean;
import com.github.stmated.plang.ty.TyValueNumber;
import com.github.stmated.plang.ty.TyValueNumberInteger;
import com.github.stmated.plang.ty.TyValueNumberPrecisionKind;
import com.github.stmated.plang.ty.TyValueNumberPrecisioned;
import com.github.stmated.plang.ty.TyValueString;
import com.github.stmated.plang.ty.util.Tys;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
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
class LLVMFunctionLowering {

  private final LLVMModuleRef module;

  private final Ctx ctx;

  /**
   * TODO: This should not be filled on-demand, it should be filled as a pass through all instructions -- so it is known at all times.
   *        We then of course need to change this Map to be.
   * <p>
   * TODO: Remove this and instead derive it from the type that it states and the state we then would expect
   *          For example if it is a const int or not a const it makes it a pointer or not.
   */
  private final Map<LLVMValueRef, Ty> overridingTypes = new HashMap<>();

  private LLVMFunctionLowering(Ctx ctx, String name) {
    this.ctx = ctx;
    this.module = LLVM.LLVMModuleCreateWithNameInContext(name, ctx.context);
  }

  public static void lower(LLVMFunctionLoweringRequest request) {
    new LLVMFunctionLowering(request.ctx(), request.fn().name()).lower_request(request);
  }

  private void lower_request(LLVMFunctionLoweringRequest request) {

    final var fn = this.createFnDeclaration(request.fn());

    firstPassTraverseNodes(request.fn().entry(), fn.fn());

    ctx.enterFunction(fn.fn(), () -> secondPassBuildNodes(request.fn().entry()));

    MirToLLVMUtils.verifyModule(module);

    request.callback().accept(new LLVMFunctionLoweringResult(module));
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
      case MirInstrStore it -> lower_store(it);
      case MirInstrGetGlobal it -> lower_get_global(it);
      default -> throw new UnexpectedExpressionException(miri);
    };

    // All instructions will always result in a valueRef, even if it is a void value.
    ctx.register(miri, valueRef);

    return valueRef;
  }

  private LLVMValueRef lower_get_global(MirInstrGetGlobal it) {

    if ("stdout".equals(it.globalName())) {
      prepare_freopen();
    }

    return LLVM.LLVMGetNamedGlobal(module, it.globalName());
  }

  private final Map<String, LLVMValueRef> addressToValueMap = new HashMap<>();

  private LLVMValueRef lower_store(MirInstrStore mir) {

    final var valueRef = lower_instruction(mir.value());

    // TODO: Need to be able to decide if should alloc or just store again to same address as before

    final var uniqueName = mir.name().getUniqueName();
    final var allocation = addressToValueMap.computeIfAbsent(uniqueName, _ -> {

      final var name = getInstrName(mir, null, mir.ty().toShortString());
      final var typeRef = MirToLLVMUtils.toLLVMType(ctx, mir.value().ty());
      return LLVM.LLVMBuildAlloca(ctx.builder, typeRef, name);
    });

    LLVM.LLVMBuildStore(ctx.builder, valueRef, allocation);

    return allocation;
  }

  private record ExternalFn(LLVMValueRef fn, LLVMTypeRef fnType, LLVMTypeRef[] params) {

  }

  private final Map<String, ExternalFn> externalFunctionMap = new HashMap<>();

  private LLVMValueRef lower_call(MirCall mir) {

    final var functionName = mir.target().name();

    // TODO: This is very bad! We should NOT create fn declarations on the fly like this.
    //        Only here right now for testing. To be removed.
    final var fn = Objects.requireNonNull(
      externalFunctionMap.computeIfAbsent(functionName, _ -> createFnDeclaration(mir.target())),
      () -> STR."Unknown function '\{functionName}'"
    );

    final var llvmArgs = new LLVMValueRef[mir.arguments().length];
    for (var i = 0; i < mir.arguments().length; i++) {

      final var arg = mir.arguments()[i];
      final var ref = lower_instruction(arg.instruction());
      final var refTy = new RefTyPair(ref, arg.instruction().ty());

      // There can be less params than args if the function uses varargs.
      if (i < fn.params().length) {

        final var mirParam = mir.target().parameters()[i];
        final var convertedPair = convert(refTy, mirParam.type(), arg.instruction());
        llvmArgs[i] = convertedPair.ref();
      } else {

        final var normalized = normalizeToType(refTy, arg.instruction());
        llvmArgs[i] = normalized.ref();
      }
    }

    final var pp = new PointerPointer<>(llvmArgs);
    return LLVM.LLVMBuildCall2(ctx.builder, fn.fnType(), fn.fn(), pp, llvmArgs.length, functionName);
  }

  private void prepare_freopen() {

    LLVMTypeRef i8PtrType = MirToLLVMUtils.toLLVMType(ctx, new TyPointer<>(Ty.CHAR).intern());
    LLVMValueRef stdout = LLVM.LLVMAddGlobal(module, i8PtrType, "stdout");
    LLVM.LLVMSetLinkage(stdout, LLVM.LLVMExternalLinkage);
    LLVM.LLVMSetAlignment(stdout, 8);
  }

  private record RefTyPair(LLVMValueRef ref, Ty ty) {

  }

  /**
   * NOTE: Would be better if we did not have overridingTypes and instead could deduce the llvm type based on stated type For example if the type is a constant
   * or a value that is not a constant (so allocated and hence a pointer)
   *
   * @param pair  The value reference & The type that the user thinks it is working with
   * @param owner The owner of the type, for dignostics and label naming purposes
   */
  private RefTyPair normalizeToType(RefTyPair pair, MirInstr owner) {

//    final var ref = pair.ref();
//    final var stated = pair.ty();

    // Given is the type that LLVM is using in the background.
    // TODO: This should be removed? There should be no overriding types, we should just know...
//    final var given = overridingTypes.getOrDefault(pair.ref(), pair.ty());
    final var expected = LLVMTys.normalize(pair.ty());

    return convert(pair, expected, owner);

//    if (given instanceof TyPointer<?> p) {
//
//      // Regular pointer should always be de-referenced upon use.
//      final var ty = p.inner();
//      final var type = MirToLLVMUtils.toLLVMType(ctx, ty);
//      final var name = getInstrName(owner, "load", STR."loaded_\{stated.toShortString()}");
//      final var loadedRef = LLVM.LLVMBuildLoad2(ctx.builder, type, ref, name);
//
//      // Then recurse, in case it is a pointer to a pointer... which we might not even want to allow?
//      return normalizeToType(new RefTyPair(loadedRef, p.inner()), owner);
//    }
//
//    return pair;
  }

  private RefTyPair convert(RefTyPair pair, Ty expected, MirInstr owner) {

//    final var given = overridingTypes.getOrDefault(pair.ref(), pair.ty());

    final var lowGiven = LLVMTys.getLowTy(pair.ty());
    final var lowExpected = LLVMTys.getLowTy(expected);

    if (lowGiven instanceof TyValueArray va) {
      if (lowExpected instanceof TyPointer) {

        // global variables are actually treated as single-item arrays.
        // So index 0 of the global item, then index 0 of that array.

        // TODO: Need to know if it actually is a global or not -- it does not have to be

        final var indices = new PointerPointer<>(2);
        indices.put(0, LLVM.LLVMConstInt(MirToLLVMUtils.toLLVMType(ctx, Ty.INTEGER), 0, 0));
        indices.put(1, LLVM.LLVMConstInt(MirToLLVMUtils.toLLVMType(ctx, Ty.INTEGER), 0, 0));

        final var targetType = va.elementType();
        final var gepType = MirToLLVMUtils.toLLVMType(ctx, targetType);

        final var name = getInstrName(owner, "gep", "gep");
        final var gep = LLVM.LLVMBuildGEP2(ctx.builder, gepType, pair.ref(), indices, 2, name);
        return new RefTyPair(gep, lowExpected);
      }
    } else if (lowGiven instanceof TyPointer<?> gp) {
      if (!(lowExpected instanceof TyPointer)) {

        // TODO: Not true that 'given' will match 'expected' here.
        //        Need to dereference it, sure, but we might need to convert the dereferenced to the expected
        final var type = MirToLLVMUtils.toLLVMType(ctx, gp.inner());
        final var name = getInstrName(owner, "load", "load");
        final var loaded = LLVM.LLVMBuildLoad2(ctx.builder, type, pair.ref(), name);
        return new RefTyPair(loaded, lowExpected);
      }
    }

    return pair;
  }

  private LLVMValueRef lower_phi(MirInstrPhi mir) {

//    Ty overridingType = null;
    final var phiValues = new PointerPointer<>(mir.operands().length);
    for (var i = 0; i < mir.operands().length; i++) {

      final var valueRef = lower_instruction(mir.operands()[i]);
//      if (overridingType == null) {
//        overridingType = overridingTypes.get(valueRef);
//      }

      phiValues.put(i, valueRef);
    }

    final var phiBlocks = new PointerPointer<>(mir.from().length);
    for (var i = 0; i < mir.from().length; i++) {

      final var blockRef = ctx.resolveBlock(mir.from()[i]);
      phiBlocks.put(i, blockRef);
    }

    final var actualTy = mir.ty(); // Objects.requireNonNullElse(overridingType, mir.ty());
    final var phiValueType = MirToLLVMUtils.toLLVMType(ctx, actualTy);
    final var phi = LLVMBuildPhi(ctx.builder, phiValueType, "result");

    LLVMAddIncoming(phi, phiValues, phiBlocks, mir.from().length);

    return phi;
  }

  private LLVMValueRef lower_jump(MirInstrJump it) {
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

    final var ref = lower_instruction(mir.instr());
    final var normalized = normalizeToType(new RefTyPair(ref, mir.instr().ty()), mir.instr());

    return LLVM.LLVMBuildRet(ctx.builder, normalized.ref());
  }

  private LLVMValueRef lower_literal(MirInstrCreateLiteral literal) {

    return switch (literal.ty()) {
      case TyValueString str -> lower_literal_string(literal.content(), str);
      case TyValueNumberInteger ni -> lower_literal_number_integer(literal, ni);
      case TyValueNumberPrecisioned np -> lower_literal_number_precisioned(literal.content(), literal, np);
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
//    overridingTypes.put(array.ref(), array.ty());

    return array.ref();
  }

  private LLVMValueRef lower_literal_number_integer(MirInstrCreateLiteral literal, TyValueNumberInteger ty) {

    // TODO: Wrong? Or can it handle octal, hex and binary? Need tests

    final var content = literal.content();
    final var v = Integer.parseInt(content, ty.radix());
    final var typeRef = MirToLLVMUtils.toLLVMType(ctx, ty);
    final var constant = LLVM.LLVMConstInt(typeRef, v, ty.signed() ? 1 : 0);

    return giveConstantOrAlloca(constant, typeRef, literal, ty);
  }

  private LLVMValueRef lower_literal_number_precisioned(String content, MirInstr instr, TyValueNumberPrecisioned ty) {

    final var v = Double.parseDouble(content);
    final var typeRef = MirToLLVMUtils.toLLVMType(ctx, ty);
    final var constant = LLVM.LLVMConstReal(typeRef, v);

    return giveConstantOrAlloca(constant, typeRef, instr, ty);
  }

  private String getInstrName(MirInstr instr, String prefix, String fallback) {

    if (instr != null) {

      if (instr.name() != null) {
        return (prefix == null ? "" : STR."\{prefix}_") + instr.name().label();
      }

      if (instr.ty() != null) {
        return (prefix == null ? "" : STR."\{prefix}_") + instr.ty().toShortString();
      }
    }

    return fallback;
  }

  private LLVMValueRef giveConstantOrAlloca(LLVMValueRef constant, LLVMTypeRef typeRef, MirInstr instr, TyValueNumber ty) {

    // TODO: Is it okay to always give back constant, and then let "store" be what makes it alloca? Need to run some code :)
//    if (ty.isConstant()) {
    return constant;
//    } else {
//
//      final var name = getInstrName(instr, "alloc", ty.toShortString());
//      final var allocation = LLVM.LLVMBuildAlloca(ctx.builder, typeRef, name);
//      LLVM.LLVMBuildStore(ctx.builder, constant, allocation);
//
//      // Override the type to be a pointer of the type.
//      overridingTypes.put(allocation, new TyPointer(ty));
//
//      return allocation;
//    }
  }

  private LLVMValueRef lower_binary_operation(MirInstrBinaryOperation mir) {

    // TODO: Need to figure out a BETTER way of knowing what we WANT it to be!
    //        Do we want to allow automatic widening of numeric types?
    //        Is the conversion something that should be done here, or added in the THIR or MIR to be explicit?
    final var lhs_pair = new RefTyPair(lower_instruction(mir.lhs()), mir.lhs().ty());
    final var rhs_pair = new RefTyPair(lower_instruction(mir.rhs()), mir.rhs().ty());

    final var nlhs = normalizeToType(lhs_pair, mir.lhs());
    final var nrhs = normalizeToType(rhs_pair, mir.rhs());

    final var elhs = widen(nlhs, nrhs.ty(), mir.lhs());
    final var erhs = widen(nrhs, nlhs.ty(), mir.rhs());

    final var lhst = elhs.ty();
    final var rhst = erhs.ty();

    final var lhs = elhs.ref();
    final var rhs = erhs.ref();

    final var instrName = mir.kind().toString().toLowerCase(Locale.ROOT);

    return switch (lhst) {
      case TyValueNumberInteger lni -> switch (rhst) {
        case TyValueNumberInteger rni -> switch (mir.kind()) {
          // TODO: Look into LLVMBuildNSWSub and LLVMBuildNUWSub (No Wrap variants -- would work if we KNOW it will not wrap)
          case ADD -> LLVM.LLVMBuildAdd(ctx.builder, lhs, rhs, instrName);
          case SUBTRACT -> LLVM.LLVMBuildSub(ctx.builder, lhs, rhs, instrName);
          case MULTIPLY -> LLVM.LLVMBuildMul(ctx.builder, lhs, rhs, instrName);
          case DIVIDE -> {
            if (lni.signed() && rni.signed()) {
              yield LLVM.LLVMBuildSDiv(ctx.builder, lhs, rhs, instrName);
            } else if (!lni.signed() && !rni.signed()) {
              yield LLVM.LLVMBuildUDiv(ctx.builder, lhs, rhs, instrName);
            } else {
              throw new NotImplementedException("Need to add signed <-> unsigned conversion");
            }
          }
          case EQUALS -> LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMIntEQ, lhs, rhs, instrName);
          case LT -> {
            if (lni.signed() && rni.signed()) {
              yield LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMIntSLT, lhs, rhs, instrName);
            } else if (!lni.signed() && !rni.signed()) {
              yield LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMIntULT, lhs, rhs, instrName);
            } else {
              throw new NotImplementedException("Need to add signed <-> unsigned conversion");
            }
          }
          case LTE -> {
            if (lni.signed() && rni.signed()) {
              yield LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMIntSLE, lhs, rhs, instrName);
            } else if (!lni.signed() && !rni.signed()) {
              yield LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMIntULE, lhs, rhs, instrName);
            } else {
              throw new NotImplementedException("Need to add signed <-> unsigned conversion");
            }
          }
          case GT -> {
            if (lni.signed() && rni.signed()) {
              yield LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMIntSGT, lhs, rhs, instrName);
            } else if (!lni.signed() && !rni.signed()) {
              yield LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMIntUGT, lhs, rhs, instrName);
            } else {
              throw new NotImplementedException("Need to add signed <-> unsigned conversion");
            }
          }
          case GTE -> {
            if (lni.signed() && rni.signed()) {
              yield LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMIntSGE, lhs, rhs, instrName);
            } else if (!lni.signed() && !rni.signed()) {
              yield LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMIntUGE, lhs, rhs, instrName);
            } else {
              throw new NotImplementedException("Need to add signed <-> unsigned conversion");
            }
          }
          default -> throw new NotImplementedException("Unknown kind");
        };
        default -> throw new NotImplementedException(STR."Unknown ty '\{rhst}'");
      };
      case TyValueNumberPrecisioned lnp -> switch (rhst) {
        case TyValueNumberPrecisioned rnp -> switch (mir.kind()) {
          case ADD -> LLVM.LLVMBuildFAdd(ctx.builder, lhs, rhs, instrName);
          case SUBTRACT -> LLVM.LLVMBuildFSub(ctx.builder, lhs, rhs, instrName);
          case MULTIPLY -> LLVM.LLVMBuildFMul(ctx.builder, lhs, rhs, instrName);
          case DIVIDE -> LLVM.LLVMBuildFDiv(ctx.builder, lhs, rhs, instrName);
          case EQUALS -> LLVM.LLVMBuildFCmp(ctx.builder, LLVM.LLVMRealOEQ, lhs, rhs, instrName);
          case LT -> LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMRealOLT, lhs, rhs, instrName);
          case LTE -> LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMRealOLE, lhs, rhs, instrName);
          case GT -> LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMRealOGT, lhs, rhs, instrName);
          case GTE -> LLVM.LLVMBuildICmp(ctx.builder, LLVM.LLVMRealOGE, lhs, rhs, instrName);
          default -> throw new NotImplementedException("Unknown kind");
        };
        default -> throw new NotImplementedException("Unknown ty");
      };
      default -> throw new NotImplementedException("Unknown ty");
    };
  }

  /**
   * To be able to do binary operations between numbers, we need to make the two values the same. We will blindly trust our type system to be correct.
   */
  private RefTyPair widen(RefTyPair pair, Ty other, MirInstr owner) {

    final var ordered = Tys.reorderBasedOnType(pair.ty(), other, Function.identity());

    final var a = ordered.a();
    final var b = ordered.b();

    if (a == b) {
      return pair;
    }

    if (a.equals(b)) {

      final var interned = Tys.intern(a);

      log.warn("There are types that should have been interned: {} and {}, interned being {}", a, b, interned);
      return pair;
    }

    final var v = pair.ref();

    if (a instanceof TyValueNumber an && b instanceof TyValueNumber bn) {
      if (an.signed() != bn.signed()) {

        // Return pair as-is, and it is then up to other code to see that they are (depending on context) incompatible.
        return pair;
      }
    }

    // TODO: Speed would increase by making all Ty created from singleton factory,
    //  where each unique is same instance, so comparison above quickly matches.
    return switch (a) {
      case TyValueNumberInteger ani -> switch (b) {
        case TyValueNumberInteger bni when ani.width() != bni.width() -> {

          if (pair.ty() == bni) {
            yield pair;
          }

          final var newWidth = Math.max(ani.width(), bni.width());
          final var newFlags = Tys.mixFlags(ani.flags(), bni.flags());
          final var newTy = Tys.intern(new TyValueNumberInteger(ani.radix(), newWidth, ani.signed(), newFlags));
          final var newType = MirToLLVMUtils.toLLVMType(ctx, newTy);

          final var built = ani.signed()
            ? LLVM.LLVMBuildSExt(ctx.builder, v, newType, getInstrName(owner, "sext", "sext"))
            : LLVM.LLVMBuildZExt(ctx.builder, v, newType, getInstrName(owner, "uext", "uext"));

          yield new RefTyPair(built, newTy);
        }
        case TyValueNumberInteger _ -> pair;
        case TyValueNumberPrecisioned bnp -> {

          if (pair.ty() == bnp) {
            yield pair;
          }

          // TODO: Probably wrong with the width; need to take into account the precision size
          final var newWidth = Math.max(ani.width(), bnp.width());
          final var newFlags = Tys.mixFlags(ani.flags(), bnp.flags());
          final var newTy = Tys.intern(new TyValueNumberPrecisioned(TyValueNumberPrecisionKind.FLOAT, newWidth, bnp.precision(), ani.signed(), newFlags));
          final var newType = MirToLLVMUtils.toLLVMType(ctx, newTy);

          final var built = ani.signed()
            ? LLVM.LLVMBuildSIToFP(ctx.builder, v, newType, getInstrName(owner, "si2fp", "si2fp"))
            : LLVM.LLVMBuildUIToFP(ctx.builder, v, newType, getInstrName(owner, "ui2fp", "ui2fp"));

          yield new RefTyPair(built, newTy);
        }
        default -> throw new NotImplementedException(STR."Implement widening for '\{b}'");
      };
      default -> throw new NotImplementedException(STR."Implement widening for '\{a}'");
    };
  }
}
