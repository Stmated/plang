package com.github.stmated.plang.llvm.lowering;

import com.github.stmated.plang.exceptions.InvalidImplementationException;
import com.github.stmated.plang.exceptions.NotImplementedException;
import com.github.stmated.plang.exceptions.UnexpectedExpressionException;
import com.github.stmated.plang.llvm.util.LLVMTys;
import com.github.stmated.plang.mir.Mir;
import com.github.stmated.plang.mir.model.MirNode;
import com.github.stmated.plang.ty.BitWidth;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyOpaque;
import com.github.stmated.plang.ty.TyPointer;
import com.github.stmated.plang.ty.TyStruct;
import com.github.stmated.plang.ty.TyValueArray;
import com.github.stmated.plang.ty.TyValueBoolean;
import com.github.stmated.plang.ty.TyValueNumber;
import com.github.stmated.plang.ty.TyValueNumberInteger;
import com.github.stmated.plang.ty.TyValueNumberPrecisioned;
import com.github.stmated.plang.ty.TyValueString;
import com.github.stmated.plang.ty.util.Pair;
import com.github.stmated.plang.ty.util.Tys;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
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

  private final MirToLLVMCtx mirToLlvmCtx;

  private final Map<String, LLVMValueRef> globalStringCache = new HashMap<>();

  private final Pattern PATTERN_INTEGER_SUFFIX = Pattern.compile("(\\d+)([iu])(\\d+)");

  private record ExternalFn(LLVMValueRef fn, LLVMTypeRef fnType, LLVMTypeRef[] params) {

  }

  private record RefTyPair(LLVMValueRef ref, Ty ty) {

  }

  private LLVMFunctionLowering(MirToLLVMCtx mirToLlvmCtx, String name) {
    this.mirToLlvmCtx = mirToLlvmCtx;
    this.module = LLVM.LLVMModuleCreateWithNameInContext(name, mirToLlvmCtx.context);
  }

  public static void lower(LLVMFunctionLoweringRequest request) {

    new LLVMFunctionLowering(request.mirToLlvmCtx(), request.name()).lower_request(request);
  }

  private void lower_request(LLVMFunctionLoweringRequest request) {

    final var fn = this.createFnDeclaration(request.fn());

    firstPassTraverseNodes(request.fn().entry(), fn.fn());

    mirToLlvmCtx.enterFunction(new Pair<>(request.fn(), fn.fn()), () -> secondPassBuildNodes(request.fn().entry()));

    MirToLLVMUtils.verifyModule(module);

    request.callback().accept(new LLVMFunctionLoweringResult(module));
  }

  private ExternalFn createFnDeclaration(Mir.InstrCreateFn instrCreateFn) {

    final var mirFnSignature = instrCreateFn.signature();
    final var fnReturnType = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, mirFnSignature.returnType());
    final var mirParameters = mirFnSignature.parameters();
    //final var actualParamCount = mirFnSignature.vararg() ? mirParameters.length - 1 : mirParameters.length;
    final var fnParams = new LLVMTypeRef[mirParameters.length];
    for (var i = 0; i < mirParameters.length; i++) {

      final var mirParam = mirParameters[i];
      final var mirParamType = mirParam.ty();

      fnParams[i] = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, mirParamType);
    }

    final var vararg = mirFnSignature.vararg() ? 1 : 0;
    final var fnType = LLVM.LLVMFunctionType(fnReturnType, new PointerPointer<>(fnParams), fnParams.length, vararg);
    final var fnName = (instrCreateFn.name() == null)
      ? "fn" // TODO: Give better name one day
      : Objects.requireNonNullElse(instrCreateFn.name().getUniqueName(), "fn");
    final var fn = LLVM.LLVMAddFunction(module, fnName, fnType);

    for (var i = 0; i < fnParams.length; i++) {

      final var mirParam = mirParameters[i];
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
      final var block = LLVM.LLVMAppendBasicBlockInContext(mirToLlvmCtx.context, fnRef, blockName);
      mirToLlvmCtx.registerBlock(node, block);
    });
  }

  private void secondPassBuildNodes(MirNode root) {
    forEachNode(root, this::move_to_and_lower_node);
  }

  private LoweringResult move_to_and_lower_node(MirNode node) {

    final var nodeBlock = mirToLlvmCtx.resolveBlock(node);
    LLVM.LLVMPositionBuilderAtEnd(mirToLlvmCtx.builder, nodeBlock);

    LoweringResult last = null;
    for (final var instruction : node.instructions()) {
      last = lower_instruction(instruction);
    }

    return last;
  }

  private LoweringResult lower_instruction(Mir.Instr miri) {

    final var ref = mirToLlvmCtx.resolveIfAvailable(miri);
    if (ref != null) {
      return ref;
    }

    return lower_instruction_inner(miri);
  }

  private LoweringResult lower_instruction_inner(Mir.Instr miri) {

    final LoweringResult valueRes = switch (miri) {
      case Mir.InstrCreateLiteral it -> lower_literal(it);
      case Mir.InstrCreateFn it -> lower_create_fn(it);
      case Mir.InstrBinaryOperation it -> lower_binary_operation(it);
      case Mir.InstrReturn it -> lower_return(it);
      case Mir.InstrConditionalJump it -> lower_conditional_jump(it);
      case Mir.InstrJump it -> lower_jump(it);
      case Mir.InstrPhi it -> lower_phi(it);
      case Mir.InstrCall it -> lower_call(it);
      case Mir.InstrStore it -> lower_store(it);
      case Mir.InstrGetGlobal it -> lower_get_global(it);
      case Mir.InstrGetParam it -> lower_get_param(it);
      case Mir.InstrCreateArray it -> lower_create_array(it);
      case Mir.InstrGetArrayElement it -> lower_get_array_element(it);
      case Mir.InstrGetStructElement it -> lower_get_struct_element(it);
      case Mir.InstrSetStructElement it -> lower_set_struct_element(it);
      case Mir.InstrCreateStruct it -> lower_create_struct(it);
      case Mir.InstrCreateInstance it -> lower_create_instance(it);
      default -> throw new UnexpectedExpressionException(miri);
    };

    // Most instructions will always result in a valueRef, even if it is a void value.
    mirToLlvmCtx.register(miri, valueRes);

    return valueRes;
  }

  private LoweringResult lower_create_instance(Mir.InstrCreateInstance it) {

    final var typeRef = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, it.ty());

    return switch (it.ty()) {
      case TyStruct struct -> {

        // TODO: Need a way of knowing if the value is a constant or not. The assignment that named the ty should alter the ty.

        // TODO: Need a way to signify scope, so we can know to Alloca or Malloc and when and how to de-allocate

        final var ptr = LLVM.LLVMBuildAlloca(mirToLlvmCtx.builder, typeRef, "");

        // We should trust the earlier stages to have given the correct arguments in the correct order.
        for (var i = 0; i < it.arguments().length; i++) {

          final var argument = it.arguments()[i];
//          final var argumentTy = argument.ty();
//          final var argumentTypeRef = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, argumentTy);
          final var valueRef = lower_instruction(argument);

          final var gep = LLVM.LLVMBuildStructGEP2(mirToLlvmCtx.builder, typeRef, ptr, i, STR."sgep\{i}");
          LLVM.LLVMBuildStore(mirToLlvmCtx.builder, valueRef.value(), gep);
        }

        yield new LoweringResult(ptr, typeRef);
      }
      default -> throw new UnexpectedExpressionException(it.ty());
    };
  }

  private LoweringResult lower_create_struct(Mir.InstrCreateStruct it) {

    // TODO: Need to use LLVMStructCreateNamed if the type is at any time self-referential, to create an opaque type

    final var typeRef = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, it.ty());
//    mirToLlvmCtx.registerType(it.ty(), typeRef);

    // TODO: Add this to the path resolving thingies -- should be based on InstrGetArrayElement?
    //        Because we can deduce the required arguments for it, no?
    //LLVM.LLVMBuildStructGEP2()

//    final var typePointerTypeRef = LLVM.LLVMPointerType(typeRef, 0);


    return new LoweringResult(null, typeRef);
  }

  private LoweringResult lower_create_array(Mir.InstrCreateArray mir) {

    // TODO: Need to be able to handle const/global arrays, and not allocate them like this every time.

    final var elementType = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, mir.elementTy());

    final var i32Type = LLVM.LLVMInt32TypeInContext(mirToLlvmCtx.context);

    final var arraySizeStatic = mir.ty().size();

    final var arraySizeRef = (arraySizeStatic != null || mir.length() == null)
      ? new LoweringResult(LLVM.LLVMConstInt(i32Type, Objects.requireNonNullElse(arraySizeStatic, mir.elements().length), 0))
      : lower_instruction(mir.length());

    // TODO: We should only use this for VERY SMALL arrays, since it is allocated on the stack!
    //        Should instead use LLVMConstArray2
    final var arrayRef = LLVM.LLVMBuildArrayAlloca(mirToLlvmCtx.builder, elementType, arraySizeRef.value(), "arr");

    final var elementRefs = new LLVMValueRef[mir.elements().length];

    for (var i = 0; i < mir.elements().length; i++) {

      final var elementRef = lower_instruction(mir.elements()[i]); ;
      elementRefs[i] = elementRef.value();

      final var indices = new PointerPointer<>(1);
      indices.put(0, LLVM.LLVMConstInt(i32Type, i, 0));

      final var elementPtr = LLVM.LLVMBuildInBoundsGEP2(mirToLlvmCtx.builder, elementType, arrayRef, indices, 1, STR."arr_ptr_\{i}");
      LLVM.LLVMBuildStore(mirToLlvmCtx.builder, elementRef.value(), elementPtr);
    }

    if (mir.length() != null && mir.elements().length > 0) {

      // We have been given a length, which might not be the same length as the elements that were given.
      if (arraySizeStatic != null) {

        // If the array type has a known size, then we'll just go with that, and add inline instructions for each initialization.
        for (var i = mir.elements().length; i < arraySizeStatic; i++) {

          final var elementRef = elementRefs[i % elementRefs.length];

          final var indices = new PointerPointer<>(1);
          indices.put(0, LLVM.LLVMConstInt(i32Type, i, 0));

          final var elementPtr = LLVM.LLVMBuildInBoundsGEP2(mirToLlvmCtx.builder, elementType, arrayRef, indices, 1, STR."arr_ptr_\{i}");
          LLVM.LLVMBuildStore(mirToLlvmCtx.builder, elementRef, elementPtr);
        }

      } else {

        // TODO: Need to implement this! Add a counter and code that loops until the right amount of init has been done.
      }
    }

    return new LoweringResult(arrayRef);
  }

  private LoweringResult lower_get_array_element(Mir.InstrGetArrayElement it) {

    final var arrayRef = lower_instruction(it.target());
    final var indexRef = lower_instruction(it.accessor());

    final var accessorTy = it.accessor().ty();
    final var expectedTy = Tys.dereference(accessorTy);
    final var dereferencedIndexRef = convert(indexRef.value(), accessorTy, expectedTy, it.accessor());

    // This is the type of the result..
    // TODO: This does currently not support things like slices and ranges. Need to properly handle all non-simple/non-integer access methods!
    // TODO: "resultType" will then not be same as a future "elementType" that we will use to construct the expected "resultType".
    final var resultType = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, it.ty());

    final var indices = new PointerPointer<>(1);
    indices.put(0, dereferencedIndexRef);

    final var ptr = LLVM.LLVMBuildGEP2(mirToLlvmCtx.builder, resultType, arrayRef.value(), indices, 1, "arr_gep");

    // TODO: Do not load every time, it is up to caller what should be done
    return new LoweringResult(LLVM.LLVMBuildLoad2(mirToLlvmCtx.builder, resultType, ptr, "arr_gep_loaded"));
  }

  private LoweringResult lower_get_struct_element(Mir.InstrGetStructElement it) {

    final var structRef = lower_instruction(it.target());

    final var structType = Objects.requireNonNullElseGet(
      structRef.type(),
      () -> {
        log.warn(STR."Did not receive the type from '\{it.target()}', will re-create using its ty");
        final var structTy = LLVMTys.normalize(it.target().ty());
        return MirToLLVMUtils.toLLVMType(mirToLlvmCtx, structTy);
      }
    );

    final var resultType = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, it.ty());

    // TODO: FIX! Problem right now is that it is a pointer to a pointer! It is indirected one too many times! ... or something ;D
    final var ptr = LLVM.LLVMBuildStructGEP2(mirToLlvmCtx.builder, structType, structRef.value(), it.index(), "struct_gep");

    // TODO: Do not load every time, it is up to caller what should be done
    final var valueRef = LLVM.LLVMBuildLoad2(mirToLlvmCtx.builder, resultType, ptr, "struct_gep_loaded");

    return new LoweringResult(valueRef, resultType);
  }

  private LoweringResult lower_set_struct_element(Mir.InstrSetStructElement it) {

    // TODO: Set it :)
    final var structRef = lower_instruction(it.target());

    final var structType = Objects.requireNonNullElseGet(
      structRef.type(),
      () -> {
        log.warn(STR."Did not receive the type from '\{it.target()}', will re-create using its ty");
        final var structTy = LLVMTys.normalize(it.target().ty());
        return MirToLLVMUtils.toLLVMType(mirToLlvmCtx, structTy);
      }
    );

    final var ptr = LLVM.LLVMBuildStructGEP2(mirToLlvmCtx.builder, structType, structRef.value(), it.index(), "struct_gep");
    final var valueRef = lower_instruction(it.value());

    // TODO: Should this return void instead?
    return new LoweringResult(LLVM.LLVMBuildStore(mirToLlvmCtx.builder, valueRef.value(), ptr));
  }

  // TODO: Need to take special care if the parameter is actually a vararg parameter!
  private LoweringResult lower_get_param(Mir.InstrGetParam mir) {

    final var iterator = mirToLlvmCtx.getFunctionIterator();
    while (iterator.hasNext()) {

      final var fn = iterator.next();
      final var parameters = fn.a().signature().parameters();

      for (var i = 0; i < parameters.length; i++) {

        // Q: Will this work well, or should we match by name instead? Since we do not allow for variable overloading.
        if (parameters[i] == mir.parameter()) {
          return new LoweringResult(LLVM.LLVMGetParam(fn.b(), i));
        }
      }
    }

    throw new IllegalArgumentException(STR."Could not find param '\{mir.parameter().name()}'");
  }

  private LoweringResult lower_create_fn(Mir.InstrCreateFn mir) {

    if (mir.entry() != null && mir.signature().vararg()) {
      throw new InvalidImplementationException("Language does not support implementing your own vararg-receiving functions");
    }

    final var declaration = createFnDeclaration(mir);
    final var fnRef = declaration.fn();

    if (mir.entry() != null) {

      final var fnBlock = LLVM.LLVMAppendBasicBlockInContext(mirToLlvmCtx.context, fnRef, "entry");
      LLVM.LLVMPositionBuilderAtEnd(mirToLlvmCtx.builder, fnBlock);

      mirToLlvmCtx.enterFunction(new Pair<>(mir, fnRef), () -> {

        for (final var instruction : mir.entry().instructions()) {
          lower_instruction(instruction);
        }
      });

      // TODO: This might not always be true/right?
      final var lastBlock = LLVM.LLVMGetLastBasicBlock(mirToLlvmCtx.getFunction().b());
      LLVM.LLVMPositionBuilderAtEnd(mirToLlvmCtx.builder, lastBlock);
    }

    return new LoweringResult(fnRef);
  }

  private LoweringResult lower_call(Mir.InstrCall mir) {

    final var mirFnSignature = mir.fnSignature();
    final var parameters = mirFnSignature.parameters();
    final var argsPtr = new PointerPointer<>(mir.arguments().length);
    for (var i = 0; i < mir.arguments().length; i++) {

      final var arg = mir.arguments()[i];
      final var ref = lower_instruction(arg.instruction());
      final var refTy = new RefTyPair(ref.value(), arg.instruction().ty());

      // There can be less params than args if the function uses varargs.
      Ty targetTy;
      if (i < parameters.length) {
        targetTy = parameters[i].ty();
      } else if (mirFnSignature.vararg()) {

        final var mirParam = parameters[parameters.length - 1];
        targetTy = switch (mirParam.ty()) {
          case TyValueArray array -> array.elementType();
          default -> throw new IllegalArgumentException("The last vararg parameter must be an array");
        };

      } else {
        throw new IllegalArgumentException("Sending more arguments than required to a non-vararg function");
      }

      final var convertedPair = convert(refTy, targetTy, arg.instruction());
      argsPtr.put(i, convertedPair.ref());
    }

    final var fnName = getInstrName(mir, "fn_res", "fn_res_");

    var fnRef = mirToLlvmCtx.resolve(mir.target());
    var fnTy = mir.target().ty();
    while (Tys.getReferenceDepth(fnTy) > 1) {

      // This is not a function pointer yet, it is a pointer to a function pointer (or even more indirection).
      // We are nice in this situation and just keep de-referencing until we have the actual function pointer.
      fnTy = Tys.dereference(fnTy);
      final var dereferencedType = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, fnTy);
      fnRef = new LoweringResult(LLVM.LLVMBuildLoad2(mirToLlvmCtx.builder, dereferencedType, fnRef.value(), "fn_ptr_deref"));
    }

    // De-reference all the way, so we get the actual function type and not the function pointer type.
    final var fnSignatureType = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, Tys.dereferenceRecursively(fnTy));

    return new LoweringResult(LLVM.LLVMBuildCall2(mirToLlvmCtx.builder, fnSignatureType, fnRef.value(), argsPtr, mir.arguments().length, fnName));
  }

  private LoweringResult lower_get_global(Mir.InstrGetGlobal it) {
    return new LoweringResult(LLVM.LLVMGetNamedGlobal(module, it.globalName()));
  }

  private LoweringResult lower_store(Mir.InstrStore mir) {

    final var valueRes = lower_instruction(mir.value());

    if (valueRes.value() != null) {

      // TODO: If it's already a ty that must be a pointer, then no point (ehehehe) storing it again

      var allocationRes = (mir.target() == null)
        ? mirToLlvmCtx.resolveIfAvailable(mir)
        : mirToLlvmCtx.resolveIfAvailable(mir.target());

      if (allocationRes == null) {

        if (LLVMTys.isPointer(mir.value().ty())) {

          // TODO: Should not need the isPointer function -- the ty SHOULD be TyPointer, if it is not then something is broken!
          return valueRes;

        } else {

          final var name = getInstrName(mir, null, mir.ty().toShortString());
          final var typeRef = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, mir.value().ty());
          final var allocationRef = LLVM.LLVMBuildAlloca(mirToLlvmCtx.builder, typeRef, name);

          allocationRes = new LoweringResult(allocationRef);
        }
      }

      LLVM.LLVMBuildStore(mirToLlvmCtx.builder, valueRes.value(), allocationRes.value());

      return allocationRes; // new LoweringResult(allocationRes.value());
    } else if (valueRes.type() != null) {
      return valueRes;
    } else {
      throw new IllegalArgumentException("No value nor type");
    }
  }

  /**
   * NOTE: Would be better if we did not have overridingTypes and instead could deduce the llvm kind based on stated kind For example if the kind is a constant
   * or a value that is not a constant (so allocated and hence a pointer)
   *
   * @param pair  The value reference & The kind that the user thinks it is working with
   * @param owner The owner of the kind, for dignostics and label naming purposes
   */
  private RefTyPair normalizeToType(RefTyPair pair, Mir.Instr owner) {

    final var expected = LLVMTys.normalize(pair.ty());

    return convert(pair, expected, owner);
  }

  private RefTyPair convert(RefTyPair pair, Ty expected, Mir.Instr owner) {

    final var lowGiven = LLVMTys.getLowTy(pair.ty());
    final var lowExpected = LLVMTys.getLowTy(expected);

    if (lowExpected instanceof TyOpaque) {
      return pair;
    }

    if (lowGiven instanceof TyValueArray va) {
      if (lowExpected instanceof TyPointer) {

        // global variables are actually treated as single-item arrays.
        // So index 0 of the global item, then index 0 of that array.

        // TODO: Need to know if it actually is a global or not -- it does not have to be

        final var indices = new PointerPointer<>(2);
        indices.put(0, LLVM.LLVMConstInt(MirToLLVMUtils.toLLVMType(mirToLlvmCtx, Ty.INTEGER), 0, 0));
        indices.put(1, LLVM.LLVMConstInt(MirToLLVMUtils.toLLVMType(mirToLlvmCtx, Ty.INTEGER), 0, 0));

        final var targetType = va.elementType();
        final var gepType = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, targetType);

        final var name = getInstrName(owner, "gep", "gep");
        final var gep = LLVM.LLVMBuildGEP2(mirToLlvmCtx.builder, gepType, pair.ref(), indices, 2, name);
        return new RefTyPair(gep, lowExpected);
      }
    } else if (Tys.getReferenceDepth(lowGiven) > Tys.getReferenceDepth(lowExpected)) {

      final var rootTypeGiven = Tys.dereferenceRecursively(lowGiven);
      final var rootTypeExpected = Tys.dereferenceRecursively(lowExpected);
      final var differences = Tys.getDifferences(rootTypeGiven, rootTypeExpected);

      if (!Tys.isGenerallyCompatible(differences)) {

        // TODO: This should NOT be thrown here -- it should have been thrown earlier in the THIR stage when types are verified.
        throw new IllegalArgumentException("Cannot convert between types! Do not check this here. Throw in earlier stages!");
      }

      final var type = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, lowExpected);
      final var name = getInstrName(owner, "load", "load");
      final var loaded = LLVM.LLVMBuildLoad2(mirToLlvmCtx.builder, type, pair.ref(), name);
      return new RefTyPair(loaded, lowExpected);
    }

    return pair;
  }

  private LLVMValueRef convert(LLVMValueRef ref, Ty given, Ty expected, Mir.Instr owner) {

    if (given instanceof TyValueArray va) {
      if (expected instanceof TyPointer) {

        // global variables are actually treated as single-item arrays.
        // So index 0 of the global item, then index 0 of that array.

        // TODO: Need to know if it actually is a global or not -- it does not have to be

        final var indices = new PointerPointer<>(2);
        indices.put(0, LLVM.LLVMConstInt(MirToLLVMUtils.toLLVMType(mirToLlvmCtx, Ty.INTEGER), 0, 0));
        indices.put(1, LLVM.LLVMConstInt(MirToLLVMUtils.toLLVMType(mirToLlvmCtx, Ty.INTEGER), 0, 0));

        final var targetType = va.elementType();
        final var gepType = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, targetType);

        final var name = getInstrName(owner, "gep", "gep");
        return LLVM.LLVMBuildGEP2(mirToLlvmCtx.builder, gepType, ref, indices, 2, name);
      }
    } else if (Tys.getReferenceDepth(given) > Tys.getReferenceDepth(expected)) {

      final var rootTypeGiven = Tys.dereferenceRecursively(given);
      final var rootTypeExpected = Tys.dereferenceRecursively(expected);
      final var differences = Tys.getDifferences(rootTypeGiven, rootTypeExpected);

      if (!Tys.isGenerallyCompatible(differences)) {

        // TODO: This should NOT be thrown here -- it should have been thrown earlier in the THIR stage when types are verified.
        throw new IllegalArgumentException("Cannot convert between types! Do not check this here. Throw in earlier stages!");
      }

      final var type = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, expected);
      final var name = getInstrName(owner, "load", "load");
      return LLVM.LLVMBuildLoad2(mirToLlvmCtx.builder, type, ref, name);
    }

    return ref;
  }

  private LoweringResult lower_phi(Mir.InstrPhi mir) {

    final var phiValues = new PointerPointer<>(mir.operands().length);
    for (var i = 0; i < mir.operands().length; i++) {

      final var valueRef = lower_instruction(mir.operands()[i]);
      phiValues.put(i, valueRef.value());
    }

    final var phiBlocks = new PointerPointer<>(mir.from().length);
    for (var i = 0; i < mir.from().length; i++) {

      final var blockRef = mirToLlvmCtx.resolveBlock(mir.from()[i]);
      phiBlocks.put(i, blockRef);
    }

    final var actualTy = mir.ty(); // Objects.requireNonNullElse(overridingType, mir.ty());
    final var phiValueType = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, actualTy);
    final var phi = LLVM.LLVMBuildPhi(mirToLlvmCtx.builder, phiValueType, "result");

    LLVM.LLVMAddIncoming(phi, phiValues, phiBlocks, mir.from().length);

    return new LoweringResult(phi);
  }

  private LoweringResult lower_jump(Mir.InstrJump it) {
    final var known = mirToLlvmCtx.resolveBlock(it.node());
    return new LoweringResult(LLVM.LLVMBuildBr(mirToLlvmCtx.builder, known));
  }

  private LoweringResult lower_conditional_jump(Mir.InstrConditionalJump it) {

    final var instr_predicate = lower_instruction(it.predicate());

    final var passBlock = mirToLlvmCtx.resolveBlock(it.pass());
    final var failBlock = mirToLlvmCtx.resolveBlock(it.fail());

    return new LoweringResult(LLVM.LLVMBuildCondBr(mirToLlvmCtx.builder, instr_predicate.value(), passBlock, failBlock));
  }

  private LoweringResult lower_return(Mir.InstrReturn mir) {

    // We do not do any casts or convert here.
    // It is up to the THIR and MIR to add compatibility instructions.

    final var ref = lower_instruction(mir.instr());
    final var normalized = normalizeToType(new RefTyPair(ref.value(), mir.instr().ty()), mir.instr());

    final var fn = mirToLlvmCtx.getFunction().a();
    final var widened = widen(normalized, fn.signature().returnType(), mir);

    return new LoweringResult(LLVM.LLVMBuildRet(mirToLlvmCtx.builder, widened.ref()));
  }

  private LoweringResult lower_literal(Mir.InstrCreateLiteral literal) {

    return switch (literal.ty()) {
      case TyValueString str -> lower_literal_string(literal.content(), str);
      case TyValueNumberInteger ni -> lower_literal_number_integer(literal, ni);
      case TyValueNumberPrecisioned np -> lower_literal_number_precisioned(literal.content(), literal, np);
      case TyValueBoolean b -> lower_literal_boolean(literal.content(), b);
      default -> throw new UnexpectedExpressionException(literal);
    };
  }

  private LoweringResult lower_literal_boolean(String strValue, TyValueBoolean b) {
    final var value = Boolean.parseBoolean(strValue);
    return new LoweringResult(LLVM.LLVMConstInt(MirToLLVMUtils.toLLVMType(mirToLlvmCtx, b), value ? 1 : 0, 0));
  }

  private LLVMValueRef getGlobalStringPtr(String str) {
    return globalStringCache.computeIfAbsent(str, s -> LLVM.LLVMBuildGlobalStringPtr(mirToLlvmCtx.builder, s, "str"));
  }

  private LoweringResult lower_literal_string(String content, TyValueString str) {

    // TODO: Like in Rust, should we separate the different kinds of strings into different types? Global, char array, others?

//    final var globalString = getGlobalStringPtr(content);
//    overridingTypes.put(globalString, new TyPointer(Ty.CHAR));
//
//    return globalString;

    final var array = MirToLLVMUtils.createCharArray(mirToLlvmCtx, module, content);
//    overridingTypes.put(array.ref(), array.ty());

    return new LoweringResult(array.ref());
  }

  private LoweringResult lower_literal_number_integer(Mir.InstrCreateLiteral literal, TyValueNumberInteger ty) {

    // TODO: Wrong? Or can it handle octal, hex and binary? Need tests

    final var content = literal.content();
    final var v = parseLiteralInteger(content, ty.radix());
    final var typeRef = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, ty);
    final var constant = LLVM.LLVMConstInt(typeRef, v, ty.signed() ? 1 : 0);

    return new LoweringResult(giveConstantOrAlloca(constant, typeRef, literal, ty));
  }

  private int parseLiteralInteger(String content, int radix) {

    final var matcher = PATTERN_INTEGER_SUFFIX.matcher(content);
    if (matcher.find()) {
      return Integer.parseInt(matcher.group(1), radix);
    }

    return Integer.parseInt(content, radix);
  }

  private LoweringResult lower_literal_number_precisioned(String content, Mir.Instr instr, TyValueNumberPrecisioned ty) {

    final var v = Double.parseDouble(content);
    final var typeRef = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, ty);
    final var constant = LLVM.LLVMConstReal(typeRef, v);

    return new LoweringResult(giveConstantOrAlloca(constant, typeRef, instr, ty));
  }

  private String getInstrName(Mir.Instr instr, String prefix, String fallback) {

    if (instr != null) {

      if (instr.name() != null) {
        return cleanInstrName((prefix == null ? "" : STR."\{prefix}_") + instr.name().label());
      }

      if (instr.ty() != null) {
        return cleanInstrName((prefix == null ? "" : STR."\{prefix}_") + instr.ty().toShortString());
      }
    }

    return cleanInstrName(fallback);
  }

  private String cleanInstrName(String name) {

    if (name.isEmpty()) {
      return "";
    }

    if (Character.isDigit(name.charAt(0))) {
      name = STR."_\{name}";
    }

    return name.replace("*", "_ptr");
  }

  private LLVMValueRef giveConstantOrAlloca(LLVMValueRef constant, LLVMTypeRef typeRef, Mir.Instr instr, TyValueNumber ty) {

    // TODO: Is it okay to always give back constant, and then let "store" be what makes it alloca? Need to run some code :)
//    if (ty.isConstant()) {
    return constant;
//    } else {
//
//      final var name = getInstrName(instr, "alloc", ty.toShortString());
//      final var allocation = LLVM.LLVMBuildAlloca(mirToLlvmCtx.builder, typeRef, name);
//      LLVM.LLVMBuildStore(mirToLlvmCtx.builder, constant, allocation);
//
//      // Override the kind to be a pointer of the kind.
//      overridingTypes.put(allocation, new TyPointer(ty));
//
//      return allocation;
//    }
  }

  private LoweringResult lower_binary_operation(Mir.InstrBinaryOperation mir) {

    // TODO: Need to figure out a BETTER way of knowing what we WANT it to be!
    //        Do we want to allow automatic widening of numeric types?
    //        Is the conversion something that should be done here, or added in the THIR or MIR to be explicit?
    final var lhs_pair = new RefTyPair(lower_instruction(mir.lhs()).value(), mir.lhs().ty());
    final var rhs_pair = new RefTyPair(lower_instruction(mir.rhs()).value(), mir.rhs().ty());

    final var nlhs = normalizeToType(lhs_pair, mir.lhs());
    final var nrhs = normalizeToType(rhs_pair, mir.rhs());

    final var elhs = widen(nlhs, nrhs.ty(), mir.lhs());
    final var erhs = widen(nrhs, nlhs.ty(), mir.rhs());

    final var lhst = elhs.ty();
    final var rhst = erhs.ty();

    final var lhs = elhs.ref();
    final var rhs = erhs.ref();

    final var instrName = mir.kind().toString().toLowerCase(Locale.ROOT);

    final LLVMValueRef valueRef = switch (lhst) {
      case TyValueNumberInteger lni -> switch (rhst) {
        case TyValueNumberInteger rni -> switch (mir.kind()) {
          // TODO: Look into LLVMBuildNSWSub and LLVMBuildNUWSub (No Wrap variants -- would work if we KNOW it will not wrap)
          case ADD -> LLVM.LLVMBuildAdd(mirToLlvmCtx.builder, lhs, rhs, instrName);
          case SUBTRACT -> LLVM.LLVMBuildSub(mirToLlvmCtx.builder, lhs, rhs, instrName);
          case MULTIPLY -> LLVM.LLVMBuildMul(mirToLlvmCtx.builder, lhs, rhs, instrName);
          case DIVIDE -> {
            if (lni.signed() && rni.signed()) {
              yield LLVM.LLVMBuildSDiv(mirToLlvmCtx.builder, lhs, rhs, instrName);
            } else if (!lni.signed() && !rni.signed()) {
              yield LLVM.LLVMBuildUDiv(mirToLlvmCtx.builder, lhs, rhs, instrName);
            } else {
              throw new NotImplementedException("Need to add signed <-> unsigned conversion");
            }
          }
          case EQUALS -> LLVM.LLVMBuildICmp(mirToLlvmCtx.builder, LLVM.LLVMIntEQ, lhs, rhs, instrName);
          case LT -> {
            if (lni.signed() && rni.signed()) {
              yield LLVM.LLVMBuildICmp(mirToLlvmCtx.builder, LLVM.LLVMIntSLT, lhs, rhs, instrName);
            } else if (!lni.signed() && !rni.signed()) {
              yield LLVM.LLVMBuildICmp(mirToLlvmCtx.builder, LLVM.LLVMIntULT, lhs, rhs, instrName);
            } else {
              throw new NotImplementedException("Need to add signed <-> unsigned conversion");
            }
          }
          case LTE -> {
            if (lni.signed() && rni.signed()) {
              yield LLVM.LLVMBuildICmp(mirToLlvmCtx.builder, LLVM.LLVMIntSLE, lhs, rhs, instrName);
            } else if (!lni.signed() && !rni.signed()) {
              yield LLVM.LLVMBuildICmp(mirToLlvmCtx.builder, LLVM.LLVMIntULE, lhs, rhs, instrName);
            } else {
              throw new NotImplementedException("Need to add signed <-> unsigned conversion");
            }
          }
          case GT -> {
            if (lni.signed() && rni.signed()) {
              yield LLVM.LLVMBuildICmp(mirToLlvmCtx.builder, LLVM.LLVMIntSGT, lhs, rhs, instrName);
            } else if (!lni.signed() && !rni.signed()) {
              yield LLVM.LLVMBuildICmp(mirToLlvmCtx.builder, LLVM.LLVMIntUGT, lhs, rhs, instrName);
            } else {
              throw new NotImplementedException("Need to add signed <-> unsigned conversion");
            }
          }
          case GTE -> {
            if (lni.signed() && rni.signed()) {
              yield LLVM.LLVMBuildICmp(mirToLlvmCtx.builder, LLVM.LLVMIntSGE, lhs, rhs, instrName);
            } else if (!lni.signed() && !rni.signed()) {
              yield LLVM.LLVMBuildICmp(mirToLlvmCtx.builder, LLVM.LLVMIntUGE, lhs, rhs, instrName);
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
          case ADD -> LLVM.LLVMBuildFAdd(mirToLlvmCtx.builder, lhs, rhs, instrName);
          case SUBTRACT -> LLVM.LLVMBuildFSub(mirToLlvmCtx.builder, lhs, rhs, instrName);
          case MULTIPLY -> LLVM.LLVMBuildFMul(mirToLlvmCtx.builder, lhs, rhs, instrName);
          case DIVIDE -> LLVM.LLVMBuildFDiv(mirToLlvmCtx.builder, lhs, rhs, instrName);
          case EQUALS -> LLVM.LLVMBuildFCmp(mirToLlvmCtx.builder, LLVM.LLVMRealOEQ, lhs, rhs, instrName);
          case LT -> LLVM.LLVMBuildICmp(mirToLlvmCtx.builder, LLVM.LLVMRealOLT, lhs, rhs, instrName);
          case LTE -> LLVM.LLVMBuildICmp(mirToLlvmCtx.builder, LLVM.LLVMRealOLE, lhs, rhs, instrName);
          case GT -> LLVM.LLVMBuildICmp(mirToLlvmCtx.builder, LLVM.LLVMRealOGT, lhs, rhs, instrName);
          case GTE -> LLVM.LLVMBuildICmp(mirToLlvmCtx.builder, LLVM.LLVMRealOGE, lhs, rhs, instrName);
          default -> throw new NotImplementedException("Unknown kind");
        };
        default -> throw new NotImplementedException("Unknown ty");
      };
      default -> throw new NotImplementedException("Unknown ty");
    };

    return new LoweringResult(valueRef);
  }

  /**
   * To be able to do binary operations between numbers, we need to make the two values the same. We will blindly trust our kind system to be correct.
   */
  private RefTyPair widen(RefTyPair pair, Ty other, Mir.Instr owner) {

    if (pair.ty() == other) {
      return pair;
    }

    if (pair.ty().equals(other)) {

      final var interned = Tys.intern(pair.ty());

      log.warn("There are types that should have been interned: {} and {}, interned being {}", pair.ty(), other, interned);
      return pair;
    }

    final var ordered = Tys.reorderBasedOnType(pair.ty(), other, Function.identity());

    final var a = ordered.a();
    final var b = ordered.b();

    final var v = pair.ref();

    // TODO: Speed would increase by making all Ty created from singleton factory,
    //  where each unique is same instance, so comparison above quickly matches.
    return switch (a) {
      case TyValueNumberInteger ani -> switch (b) {
        case TyValueNumberInteger bni when ani.width() != bni.width() -> {

          final var newWidth = BitWidth.merge(ani.width(), bni.width());
          final var newFlags = Tys.mixFlags(ani.flags(), bni.flags());
          final var newTy = Tys.intern(new TyValueNumberInteger(ani.radix(), newWidth, ani.signed() || bni.signed(), newFlags));
          final var newType = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, newTy);

          final var built = ani.signed()
            ? LLVM.LLVMBuildSExtOrBitCast(mirToLlvmCtx.builder, v, newType, getInstrName(owner, "sext", "sext"))
            : LLVM.LLVMBuildZExtOrBitCast(mirToLlvmCtx.builder, v, newType, getInstrName(owner, "uext", "uext"));

          yield new RefTyPair(built, newTy);
        }
        case TyValueNumberInteger _ -> pair;
        case TyValueNumberPrecisioned bnp -> {

          if (pair.ty() == bnp) {
            yield pair;
          }

          // TODO: Probably wrong with the width; need to take into account the precision size

          final var newTy = Ty.FLOAT.toBuilder()
            .width(BitWidth.merge(ani.width(), bnp.width()))
            .flags(Tys.mixFlags(ani.flags(), bnp.flags()))
            .signed(ani.signed())
            .build()
            .intern();

          final var newType = MirToLLVMUtils.toLLVMType(mirToLlvmCtx, newTy);

          final var built = ani.signed()
            ? LLVM.LLVMBuildSIToFP(mirToLlvmCtx.builder, v, newType, getInstrName(owner, "si2fp", "si2fp"))
            : LLVM.LLVMBuildUIToFP(mirToLlvmCtx.builder, v, newType, getInstrName(owner, "ui2fp", "ui2fp"));

          yield new RefTyPair(built, newTy);
        }
        default -> throw new NotImplementedException(STR."Implement widening for '\{b}'");
      };
      default -> throw new NotImplementedException(STR."Implement widening for '\{a}'");
    };
  }
}
