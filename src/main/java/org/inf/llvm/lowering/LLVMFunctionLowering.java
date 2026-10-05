package org.inf.llvm.lowering;

import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;
import org.bytedeco.llvm.global.LLVM;
import org.inf.exceptions.NotImplementedException;
import org.inf.mir.Mir;
import org.inf.mir.MirLoweringResult;
import org.inf.mir.MirTypes;
import org.inf.mir.model.MirFunction;
import org.inf.mir.model.MirNode;
import org.inf.ty.*;
import org.inf.util.IntegerLiterals;

import java.util.*;

/** Serializes already evaluated MIR values; operands never trigger instruction lowering. */
final class LLVMFunctionLowering {

  private final LLVMContextRef context;
  private final LLVMBuilderRef builder;
  private final LLVMModuleRef module;
  final LLVMTypeResolver types;
  private final Map<MirFunction, LLVMValueRef> functions = new IdentityHashMap<>();
  private final Map<MirNode, LLVMBasicBlockRef> blocks = new IdentityHashMap<>();
  private final Map<Integer, LLVMValueRef> values = new HashMap<>();
  private final Map<Integer, LLVMValueRef> locals = new HashMap<>();
  private final Map<String, LLVMValueRef> strings = new HashMap<>();
  private LLVMValueRef function;
  private LLVMValueRef allocator;
  private LLVMTypeRef allocatorType;
  private String cleanupName;

  LLVMFunctionLowering(LLVMContextRef context, LLVMBuilderRef builder, LLVMModuleRef module) {
    this.context = context;
    this.builder = builder;
    this.module = module;
    types = new LLVMTypeResolver(context, new Cache<>());
  }

  void lower(MirLoweringResult input) {
    for (final var fn : input.functions()) {
      functions.put(fn, LLVM.LLVMAddFunction(module, fn.name(), types.resolve(MirTypes.functionType(fn.signature()))));
    }
    for (final var fn : input.functions()) {
      if (!fn.external()) {
        lower(fn);
      }
    }
  }

  LLVMValueRef function(MirFunction fn) {
    return Objects.requireNonNull(functions.get(fn), "Function not declared in MIR module: " + fn.name());
  }

  String cleanupName() {
    return cleanupName;
  }

  private void lower(MirFunction fn) {
    values.clear();
    locals.clear();
    blocks.clear();
    function = function(fn);
    for (final var block : fn.blocks()) {
      blocks.put(block, LLVM.LLVMAppendBasicBlockInContext(context, function, block.name()));
    }
    LLVM.LLVMPositionBuilderAtEnd(builder, blocks.get(fn.entry()));
    for (final var local : fn.locals()) {
      locals.put(local.id(), LLVM.LLVMBuildAlloca(builder, types.resolve(local.ty()), local.name()));
    }
    final var postorder = new ArrayList<MirNode>();
    visit(fn.entry(), Collections.newSetFromMap(new IdentityHashMap<>()), postorder);
    Collections.reverse(postorder);
    for (final var block : postorder) {
      LLVM.LLVMPositionBuilderAtEnd(builder, blocks.get(block));
      for (final var instruction : block.instructions()) {
        final var value = instruction(instruction);
        if (instruction.result() != null) {
          if (values.putIfAbsent(instruction.result().id(), Objects.requireNonNull(value)) != null) {
            throw new IllegalArgumentException("Duplicate MIR value " + instruction.result());
          }
        }
      }
      terminator(block.terminator());
    }
    for (final var block : fn.blocks()) {
      if (!postorder.contains(block)) {
        LLVM.LLVMPositionBuilderAtEnd(builder, blocks.get(block));
        LLVM.LLVMBuildUnreachable(builder);
      }
    }
  }

  private void visit(MirNode block, Set<MirNode> seen, List<MirNode> order) {
    if (seen.add(block)) {
      for (final var successor : block.successors()) {
        visit(successor, seen, order);
      }
      order.add(block);
    }
  }

  private LLVMValueRef operand(Mir.Operand operand) {
    return switch (operand) {
      case Mir.Value value -> Objects.requireNonNull(values.get(value.id()), "MIR value has not been defined: " + value);
      case Mir.FunctionRef ref -> function(ref.function());
      case Mir.Constant constant -> constant(constant);
      case Mir.Unit _ -> throw new IllegalArgumentException("Void has no LLVM value");
    };
  }

  private LLVMValueRef constant(Mir.Constant constant) {
    final var content = constant.content();
    return switch (constant.ty()) {
      case TyValueBoolean _ -> LLVM.LLVMConstInt(types.resolve(Ty.BOOLEAN), Boolean.parseBoolean(content) ? 1 : 0, 0);
      case TyValueNumberInteger integer -> {
        final var number = IntegerLiterals.parse(content, integer.radix());
        yield LLVM.LLVMConstIntOfString(types.resolve(integer), number.toString(), (byte) 10);
      }
      case TyValueNumberPrecisioned real ->
        LLVM.LLVMConstReal(types.resolve(real), Double.parseDouble(content.replace("_", "").replaceFirst("[fFdD]\\d*$", "")));
      case TyValueString _ -> strings.computeIfAbsent(content, s -> LLVM.LLVMBuildGlobalStringPtr(builder, s, "str"));
      default -> throw new NotImplementedException("LLVM constant: " + constant.ty());
    };
  }

  private LLVMValueRef place(Mir.Place place) {
    return switch (place) {
      case Mir.Local local -> Objects.requireNonNull(locals.get(local.id()), "Unknown local " + local);
      case Mir.Field field -> LLVM.LLVMBuildStructGEP2(builder, types.resolve(MirTypes.pointee(field.target().ty())),
        operand(field.target()), field.index(), "field");
      case Mir.Element element -> {
        final var array = (TyValueArray) MirTypes.pointee(element.target().ty());
        yield LLVM.LLVMBuildGEP2(builder, types.resolve(MirTypes.valueType(array.elementType())), operand(element.target()),
          new PointerPointer<>(new LLVMValueRef[]{operand(element.index())}), 1, "element");
      }
    };
  }

  private LLVMValueRef instruction(Mir.Instruction instruction) {
    return switch (instruction) {
      case Mir.Parameter parameter -> LLVM.LLVMGetParam(function, parameter.index());
      case Mir.Load load -> LLVM.LLVMBuildLoad2(builder, types.resolve(load.result().ty()), place(load.place()), "load");
      case Mir.Store store -> LLVM.LLVMBuildStore(builder, operand(store.value()), place(store.place()));
      case Mir.Binary binary -> binary(binary);
      case Mir.Convert convert -> convert(operand(convert.value()), convert.value().ty(), convert.result().ty());
      case Mir.UnionVariant variant -> unionVariant((TyUnion) variant.result().ty(), variant.variant(),
        variant.value() == Mir.Unit.INSTANCE ? null : operand(variant.value()));
      case Mir.Call call -> {
        final var arguments = new LLVMValueRef[call.arguments().size()];
        for (var i = 0; i < arguments.length; i++) {
          final var argument = call.arguments().get(i);
          arguments[i] = operand(argument);
        }
        yield LLVM.LLVMBuildCall2(builder, types.resolve(MirTypes.functionType(call.signature())), operand(call.target()),
          new PointerPointer<>(arguments), arguments.length, call.result() == null ? "" : "call");
      }
      case Mir.NewStruct struct -> {
        final var layout = types.resolve(MirTypes.pointee(struct.result().ty()));
        final var allocation = allocate(LLVM.LLVMSizeOf(layout));
        for (var i = 0; i < struct.fields().size(); i++) {
          LLVM.LLVMBuildStore(builder, operand(struct.fields().get(i)), LLVM.LLVMBuildStructGEP2(builder, layout, allocation, i, "field"));
        }
        yield allocation;
      }
      case Mir.NewArray array -> newArray(array);
    };
  }

  private LLVMValueRef newArray(Mir.NewArray array) {
    final var arrayTy = (TyValueArray) MirTypes.pointee(array.result().ty());
    final var elementType = types.resolve(MirTypes.valueType(arrayTy.elementType()));
    final var rawLength = operand(array.length());
    if (((TyValueNumberInteger) array.length().ty()).width().value() > 64) {
      final var maximumLength = LLVM.LLVMConstIntOfString(types.resolve(array.length().ty()), Long.toString(Long.MAX_VALUE), (byte) 10);
      guard(LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntULE, rawLength, maximumLength, "array.length.fits"), "array.length.width");
    }
    final var length = convert(rawLength, array.length().ty(), Ty.LONG);
    guard(LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntSGE, length, integer(array.elements().size()), "array.length.valid"), "array.length");
    if (arrayTy.size() != null) {
      guard(LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntEQ, length, integer(arrayTy.size()), "array.size.matches"), "array.size");
    }
    final var elementSize = LLVM.LLVMSizeOf(elementType);
    final var maximum = LLVM.LLVMBuildUDiv(builder, integer(Long.MAX_VALUE), elementSize, "array.maximum");
    guard(LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntULE, length, maximum, "array.size.fits"), "array.overflow");
    final var bytes = LLVM.LLVMBuildMul(builder, length, elementSize, "array.bytes");
    final var allocation = allocate(bytes);
    if (array.elements().isEmpty()) {
      return allocation;
    }
    final var elements = array.elements().stream().map(this::operand).toList();
    final var origin = LLVM.LLVMGetInsertBlock(builder);
    final var condition = LLVM.LLVMAppendBasicBlockInContext(context, function, "array.condition");
    final var body = LLVM.LLVMAppendBasicBlockInContext(context, function, "array.body");
    final var end = LLVM.LLVMAppendBasicBlockInContext(context, function, "array.end");
    LLVM.LLVMBuildBr(builder, condition);
    LLVM.LLVMPositionBuilderAtEnd(builder, condition);
    final var index = LLVM.LLVMBuildPhi(builder, types.resolve(Ty.LONG), "index");
    LLVM.LLVMAddIncoming(index, new PointerPointer<>(new LLVMValueRef[]{integer(0)}), new PointerPointer<>(new LLVMBasicBlockRef[]{origin}), 1);
    final var more = LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntULT, index, length, "more");
    LLVM.LLVMBuildCondBr(builder, more, body, end);
    LLVM.LLVMPositionBuilderAtEnd(builder, body);
    final var cyclicIndex = LLVM.LLVMBuildURem(builder, index, integer(elements.size()), "cyclic.index");
    var value = elements.getFirst();
    for (var i = 1; i < elements.size(); i++) {
      value = LLVM.LLVMBuildSelect(builder, LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntEQ, cyclicIndex, integer(i), "matches"),
        elements.get(i), value, "initializer");
    }
    final var address = LLVM.LLVMBuildGEP2(builder, elementType, allocation, new PointerPointer<>(new LLVMValueRef[]{index}), 1, "element");
    LLVM.LLVMBuildStore(builder, value, address);
    final var next = LLVM.LLVMBuildAdd(builder, index, integer(1), "next");
    LLVM.LLVMBuildBr(builder, condition);
    LLVM.LLVMAddIncoming(index, new PointerPointer<>(new LLVMValueRef[]{next}), new PointerPointer<>(new LLVMBasicBlockRef[]{body}), 1);
    LLVM.LLVMPositionBuilderAtEnd(builder, end);
    return allocation;
  }

  private LLVMValueRef integer(long value) {
    return LLVM.LLVMConstInt(types.resolve(Ty.LONG), value, 0);
  }

  private void guard(LLVMValueRef predicate, String name) {
    final var owner = LLVM.LLVMGetBasicBlockParent(LLVM.LLVMGetInsertBlock(builder));
    final var valid = LLVM.LLVMAppendBasicBlockInContext(context, owner, name + ".valid");
    final var invalid = LLVM.LLVMAppendBasicBlockInContext(context, owner, name + ".invalid");
    LLVM.LLVMBuildCondBr(builder, predicate, valid, invalid);
    LLVM.LLVMPositionBuilderAtEnd(builder, invalid);
    final var trapType = LLVM.LLVMFunctionType(types.resolve(Ty.VOID), new PointerPointer<LLVMTypeRef>(0), 0, 0);
    var trap = LLVM.LLVMGetNamedFunction(module, "llvm.trap");
    if (trap == null || trap.isNull()) {
      trap = LLVM.LLVMAddFunction(module, "llvm.trap", trapType);
    }
    LLVM.LLVMBuildCall2(builder, trapType, trap, new PointerPointer<LLVMValueRef>(0), 0, "");
    LLVM.LLVMBuildUnreachable(builder);
    LLVM.LLVMPositionBuilderAtEnd(builder, valid);
  }

  /** All current aggregate handles are invocation-owned, including those returned between language functions. */
  private LLVMValueRef allocate(LLVMValueRef size) {
    if (allocator == null) {
      createAllocator();
    }
    return LLVM.LLVMBuildCall2(builder, allocatorType, allocator, new PointerPointer<>(new LLVMValueRef[]{size}), 1, "allocation");
  }

  private void createAllocator() {
    final var insertion = LLVM.LLVMGetInsertBlock(builder);
    final var pointer = LLVM.LLVMPointerTypeInContext(context, 0);
    final var allocationNodeType = LLVM.LLVMStructTypeInContext(context,
      new PointerPointer<>(new LLVMTypeRef[]{pointer, pointer}), 2, 0);
    final var head = LLVM.LLVMAddGlobal(module, pointer, "__inf_allocations");
    LLVM.LLVMSetInitializer(head, LLVM.LLVMConstNull(pointer));
    LLVM.LLVMSetLinkage(head, LLVM.LLVMInternalLinkage);
    allocatorType = LLVM.LLVMFunctionType(pointer, new PointerPointer<>(new LLVMTypeRef[]{types.resolve(Ty.LONG)}), 1, 0);
    allocator = LLVM.LLVMAddFunction(module, "__inf_allocate", allocatorType);
    LLVM.LLVMSetLinkage(allocator, LLVM.LLVMInternalLinkage);
    LLVM.LLVMPositionBuilderAtEnd(builder, LLVM.LLVMAppendBasicBlockInContext(context, allocator, "entry"));
    final var size = LLVM.LLVMGetParam(allocator, 0);
    final var sizeType = LLVM.LLVMIntPtrTypeInContext(context, LLVM.LLVMGetModuleDataLayout(module));
    final var sizeWidth = LLVM.LLVMGetIntTypeWidth(sizeType);
    if (sizeWidth < 64) {
      guard(LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntULE, size, integer(-1L >>> (64 - sizeWidth)), "size.fits"), "allocation.width");
    }
    final var mallocType = LLVM.LLVMFunctionType(pointer, new PointerPointer<>(new LLVMTypeRef[]{sizeType}), 1, 0);
    var malloc = LLVM.LLVMGetNamedFunction(module, "malloc");
    if (malloc == null || malloc.isNull()) {
      malloc = LLVM.LLVMAddFunction(module, "malloc", mallocType);
    } else if (LLVM.LLVMGlobalGetValueType(malloc).address() != mallocType.address()) {
      throw new IllegalArgumentException("Conflicting declaration of the native allocator malloc");
    }
    final var nonzeroSize = LLVM.LLVMBuildSelect(builder,
      LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntEQ, size, integer(0), "empty"), integer(1), size, "allocation.size");
    final var nativeSize = LLVM.LLVMBuildIntCast2(builder, nonzeroSize, sizeType, 0, "native.size");
    final var data = LLVM.LLVMBuildCall2(builder, mallocType, malloc, new PointerPointer<>(new LLVMValueRef[]{nativeSize}), 1, "data");
    guard(LLVM.LLVMBuildIsNotNull(builder, data, "allocated"), "allocation");
    final var nodeSize = LLVM.LLVMBuildIntCast2(builder, LLVM.LLVMSizeOf(allocationNodeType), sizeType, 0, "node.size");
    final var node = LLVM.LLVMBuildCall2(builder, mallocType, malloc, new PointerPointer<>(new LLVMValueRef[]{nodeSize}), 1, "allocation.node");
    guard(LLVM.LLVMBuildIsNotNull(builder, node, "tracked"), "allocation.tracking");
    final var previous = LLVM.LLVMBuildLoad2(builder, pointer, head, "previous");
    LLVM.LLVMBuildStore(builder, previous, LLVM.LLVMBuildStructGEP2(builder, allocationNodeType, node, 0, "next.address"));
    LLVM.LLVMBuildStore(builder, data, LLVM.LLVMBuildStructGEP2(builder, allocationNodeType, node, 1, "data.address"));
    LLVM.LLVMBuildStore(builder, node, head);
    LLVM.LLVMBuildRet(builder, data);

    final var cleanupType = LLVM.LLVMFunctionType(types.resolve(Ty.VOID), new PointerPointer<LLVMTypeRef>(0), 0, 0);
    final var cleanup = LLVM.LLVMAddFunction(module, "__inf_cleanup", cleanupType);
    cleanupName = LLVM.LLVMGetValueName(cleanup).getString();
    final var entry = LLVM.LLVMAppendBasicBlockInContext(context, cleanup, "entry");
    final var condition = LLVM.LLVMAppendBasicBlockInContext(context, cleanup, "condition");
    final var loop = LLVM.LLVMAppendBasicBlockInContext(context, cleanup, "loop");
    final var done = LLVM.LLVMAppendBasicBlockInContext(context, cleanup, "done");
    LLVM.LLVMPositionBuilderAtEnd(builder, entry);
    LLVM.LLVMBuildBr(builder, condition);
    LLVM.LLVMPositionBuilderAtEnd(builder, condition);
    final var current = LLVM.LLVMBuildLoad2(builder, pointer, head, "current");
    LLVM.LLVMBuildCondBr(builder, LLVM.LLVMBuildIsNull(builder, current, "empty"), done, loop);
    LLVM.LLVMPositionBuilderAtEnd(builder, loop);
    final var next = LLVM.LLVMBuildLoad2(builder, pointer,
      LLVM.LLVMBuildStructGEP2(builder, allocationNodeType, current, 0, "next.address"), "next");
    final var payload = LLVM.LLVMBuildLoad2(builder, pointer,
      LLVM.LLVMBuildStructGEP2(builder, allocationNodeType, current, 1, "data.address"), "data");
    LLVM.LLVMBuildFree(builder, payload);
    LLVM.LLVMBuildFree(builder, current);
    LLVM.LLVMBuildStore(builder, next, head);
    LLVM.LLVMBuildBr(builder, condition);
    LLVM.LLVMPositionBuilderAtEnd(builder, done);
    LLVM.LLVMBuildRetVoid(builder);
    LLVM.LLVMPositionBuilderAtEnd(builder, insertion);
  }

  static int payloadIndex(TyUnion union, int variant) {
    var index = 1;
    for (var i = 0; i < variant; i++) {
      if (!union.types()[i].equals(Ty.VOID)) {
        index++;
      }
    }
    return index;
  }

  private LLVMValueRef unionVariant(TyUnion union, int variant, LLVMValueRef payload) {
    var value = LLVM.LLVMConstNull(types.resolve(union));
    value = LLVM.LLVMBuildInsertValue(builder, value, LLVM.LLVMConstInt(types.resolve(Ty.INTEGER), variant, 0), 0, "union.tag");
    if (!union.types()[variant].equals(Ty.VOID)) {
      value = LLVM.LLVMBuildInsertValue(builder, value, Objects.requireNonNull(payload), payloadIndex(union, variant), "union.payload");
    }
    return value;
  }

  private LLVMValueRef convert(LLVMValueRef value, Ty from, Ty to) {
    if (from.equals(to)) {
      return value;
    }
    if (from instanceof TyUnion source && to instanceof TyUnion target) {
      final var tag = LLVM.LLVMBuildExtractValue(builder, value, 0, "union.tag");
      var converted = LLVM.LLVMConstNull(types.resolve(target));
      for (var i = 0; i < source.types().length; i++) {
        final var variant = source.types()[i];
        final var targetIndex = Arrays.asList(target.types()).indexOf(variant);
        if (targetIndex < 0) {
          throw new IllegalArgumentException("Union widening loses variant " + variant);
        }
        final var payload = variant.equals(Ty.VOID) ? null :
          LLVM.LLVMBuildExtractValue(builder, value, payloadIndex(source, i), "union.payload");
        final var injected = unionVariant(target, targetIndex, payload);
        final var active = LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntEQ, tag,
          LLVM.LLVMConstInt(types.resolve(Ty.INTEGER), i, 0), "union.active");
        converted = LLVM.LLVMBuildSelect(builder, active, injected, converted, "union.widen");
      }
      return converted;
    }
    final var target = types.resolve(to);
    if (from instanceof TyValueNumberInteger || from instanceof TyValueBoolean) {
      final var signed = from instanceof TyValueNumberInteger integer && integer.signed();
      if (to instanceof TyValueNumberInteger || to instanceof TyValueBoolean) {
        return LLVM.LLVMBuildIntCast2(builder, value, target, signed ? 1 : 0, "int.cast");
      }
      if (to instanceof TyValueNumberPrecisioned) {
        return signed ? LLVM.LLVMBuildSIToFP(builder, value, target, "float.cast") :
          LLVM.LLVMBuildUIToFP(builder, value, target, "float.cast");
      }
    } else if (from instanceof TyValueNumberPrecisioned) {
      if (to instanceof TyValueNumberPrecisioned) {
        return LLVM.LLVMBuildFPCast(builder, value, target, "float.cast");
      }
      if (to instanceof TyValueNumberInteger integer) {
        return integer.signed() ? LLVM.LLVMBuildFPToSI(builder, value, target, "int.cast") :
          LLVM.LLVMBuildFPToUI(builder, value, target, "int.cast");
      }
    } else if ((from instanceof TyPointer<?> || from instanceof TyValueString) &&
               (to instanceof TyPointer<?> || to instanceof TyValueString)) {
      return LLVM.LLVMBuildPointerCast(builder, value, target, "pointer.cast");
    }
    throw new NotImplementedException("MIR conversion from " + from + " to " + to);
  }

  private LLVMValueRef binary(Mir.Binary binary) {
    final var lhs = operand(binary.lhs());
    final var rhs = operand(binary.rhs());
    final var floating = binary.lhs().ty() instanceof TyValueNumberPrecisioned;
    final var signed = binary.lhs().ty() instanceof TyValueNumberInteger integer && integer.signed();
    final var name = binary.kind().name().toLowerCase(Locale.ROOT);
    if (floating) {
      return switch (binary.kind()) {
        case ADD -> LLVM.LLVMBuildFAdd(builder, lhs, rhs, name);
        case SUBTRACT -> LLVM.LLVMBuildFSub(builder, lhs, rhs, name);
        case MULTIPLY -> LLVM.LLVMBuildFMul(builder, lhs, rhs, name);
        case DIVIDE -> LLVM.LLVMBuildFDiv(builder, lhs, rhs, name);
        case MODULUS, REMAINDER -> LLVM.LLVMBuildFRem(builder, lhs, rhs, name);
        case EQUALS, IS -> LLVM.LLVMBuildFCmp(builder, LLVM.LLVMRealOEQ, lhs, rhs, name);
        case NOT_EQUALS -> LLVM.LLVMBuildFCmp(builder, LLVM.LLVMRealUNE, lhs, rhs, name);
        case LT -> LLVM.LLVMBuildFCmp(builder, LLVM.LLVMRealOLT, lhs, rhs, name);
        case LTE -> LLVM.LLVMBuildFCmp(builder, LLVM.LLVMRealOLE, lhs, rhs, name);
        case GT -> LLVM.LLVMBuildFCmp(builder, LLVM.LLVMRealOGT, lhs, rhs, name);
        case GTE -> LLVM.LLVMBuildFCmp(builder, LLVM.LLVMRealOGE, lhs, rhs, name);
        default -> throw new NotImplementedException("Floating binary operation " + binary.kind());
      };
    }
    return switch (binary.kind()) {
      case ADD -> LLVM.LLVMBuildAdd(builder, lhs, rhs, name);
      case SUBTRACT -> LLVM.LLVMBuildSub(builder, lhs, rhs, name);
      case MULTIPLY -> LLVM.LLVMBuildMul(builder, lhs, rhs, name);
      case DIVIDE -> signed ? LLVM.LLVMBuildSDiv(builder, lhs, rhs, name) : LLVM.LLVMBuildUDiv(builder, lhs, rhs, name);
      case MODULUS, REMAINDER -> signed ? LLVM.LLVMBuildSRem(builder, lhs, rhs, name) : LLVM.LLVMBuildURem(builder, lhs, rhs, name);
      case BIT_SHIFT_LEFT -> LLVM.LLVMBuildShl(builder, lhs, rhs, name);
      case BIT_SHIFT_RIGHT -> signed ? LLVM.LLVMBuildAShr(builder, lhs, rhs, name) : LLVM.LLVMBuildLShr(builder, lhs, rhs, name);
      case BIT_OR, OR -> LLVM.LLVMBuildOr(builder, lhs, rhs, name);
      case BIT_AND, AND -> LLVM.LLVMBuildAnd(builder, lhs, rhs, name);
      case EQUALS, IS -> LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntEQ, lhs, rhs, name);
      case NOT_EQUALS -> LLVM.LLVMBuildICmp(builder, LLVM.LLVMIntNE, lhs, rhs, name);
      case LT -> LLVM.LLVMBuildICmp(builder, signed ? LLVM.LLVMIntSLT : LLVM.LLVMIntULT, lhs, rhs, name);
      case LTE -> LLVM.LLVMBuildICmp(builder, signed ? LLVM.LLVMIntSLE : LLVM.LLVMIntULE, lhs, rhs, name);
      case GT -> LLVM.LLVMBuildICmp(builder, signed ? LLVM.LLVMIntSGT : LLVM.LLVMIntUGT, lhs, rhs, name);
      case GTE -> LLVM.LLVMBuildICmp(builder, signed ? LLVM.LLVMIntSGE : LLVM.LLVMIntUGE, lhs, rhs, name);
      default -> throw new NotImplementedException("Integer binary operation " + binary.kind());
    };
  }

  private void terminator(Mir.Terminator terminator) {
    switch (terminator) {
      case Mir.Jump jump -> LLVM.LLVMBuildBr(builder, blocks.get(jump.target()));
      case Mir.Branch branch -> LLVM.LLVMBuildCondBr(builder, operand(branch.predicate()), blocks.get(branch.pass()), blocks.get(branch.fail()));
      case Mir.Return ret -> {
        if (ret.value() == Mir.Unit.INSTANCE) {
          LLVM.LLVMBuildRetVoid(builder);
        } else {
          LLVM.LLVMBuildRet(builder, operand(ret.value()));
        }
      }
      case Mir.Unreachable _ -> LLVM.LLVMBuildUnreachable(builder);
    }
  }
}
