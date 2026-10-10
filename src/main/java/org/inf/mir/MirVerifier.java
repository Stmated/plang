package org.inf.mir;

import org.inf.mir.model.MirFnSignature;
import org.inf.mir.model.MirFunction;
import org.inf.mir.model.MirNode;
import org.inf.ty.*;
import org.inf.ty.util.TupleTypes;
import org.inf.ty.util.TypeComparison;
import org.inf.ty.util.UnionTypes;

import java.math.BigInteger;
import java.util.*;

/** Checks fully lowered MIR without inferring types or repairing control flow. */
public final class MirVerifier {

  private final Set<MirFunction> functions = identitySet();
  private final Set<MirNode> blocks = identitySet();
  private final Set<Mir.Instruction> instructions = identitySet();
  private final Set<Mir.Terminator> terminators = identitySet();
  private final Set<Mir.Value> results = identitySet();
  private final Set<Mir.Local> locals = identitySet();

  private record Definition(MirNode block, int index) {
  }

  private MirVerifier() {
  }

  public static void verify(MirLoweringResult module) {
    require(module != null, "Missing MIR module");
    final var verifier = new MirVerifier();
    for (final var function : module.functions()) {
      require(function != null && verifier.functions.add(function), "Duplicate module function");
      signature(function.signature());
    }
    require(verifier.functions.contains(module.script()) && !module.script().external(),
      "Module script must be an owned, defined function");
    for (final var function : module.functions()) {
      verifier.function(function);
    }
  }

  private void function(MirFunction function) {
    final var ownedBlocks = identitySet(function.blocks());
    final var ownedLocals = identitySet(function.locals());
    final var definitions = new IdentityHashMap<Mir.Value, Definition>();
    final var valueIds = new HashSet<Integer>();
    final var localIds = new HashSet<Integer>();
    final var parameters = new HashSet<Integer>();
    final var predecessors = new IdentityHashMap<MirNode, Set<MirNode>>();
    for (final var local : function.locals()) {
      require(locals.add(local) && localIds.add(local.id()) && local.id() >= 0,
        "Duplicate local in " + function.name());
      valueType(local.ty());
    }
    require(!function.external() || function.locals().isEmpty(), "External function has locals");
    for (final var block : function.blocks()) {
      require(blocks.add(block), "Block belongs to multiple functions: " + block);
      require(block.terminator() != null, "Unterminated block: " + block);
      require(terminators.add(block.terminator()), "Terminator belongs to multiple blocks: " + block);
      predecessors.put(block, identitySet());
      for (var i = 0; i < block.instructions().size(); i++) {
        final var instruction = block.instructions().get(i);
        require(instructions.add(instruction), "Instruction belongs to multiple blocks: " + block);
        final var result = instruction.result();
        if (result != null) {
          valueType(result.ty());
          require(result.id() >= 0 && valueIds.add(result.id()) && results.add(result),
            "Duplicate value definition in " + function.name());
          definitions.put(result, new Definition(block, i));
        } else {
          require(instruction instanceof Mir.Store || instruction instanceof Mir.Call,
            "Instruction is missing a result in " + block);
        }
        if (instruction instanceof Mir.Parameter parameter) {
          require(block == function.entry() && parameters.add(parameter.index()),
            "Parameter must be defined once in the entry block");
        }
      }
    }
    if (function.external()) {
      return;
    }
    for (final var block : function.blocks()) {
      for (final var successor : block.successors()) {
        require(ownedBlocks.contains(successor), "Cross-function or unowned CFG edge from " + block);
        require(successor != function.entry(), "Function entry has an incoming CFG edge");
        predecessors.get(successor).add(block);
      }
    }
    final var reachable = reachable(function.entry());
    final var dominators = dominators(function, predecessors, reachable);
    for (final var block : function.blocks()) {
      for (var i = 0; i < block.instructions().size(); i++) {
        final var instruction = block.instructions().get(i);
        instruction(instruction, function, ownedLocals);
        for (final var operand : instruction.operands()) {
          operand(operand, block, i, definitions, dominators);
        }
      }
      final var terminator = block.terminator();
      for (final var operand : terminator.operands()) {
        operand(operand, block, block.instructions().size(), definitions, dominators);
      }
      switch (terminator) {
        case Mir.Branch branch -> same(Ty.BOOLEAN, branch.predicate().ty(), "Branch predicate");
        case Mir.Return ret -> same(function.signature().returnType(), ret.value().ty(), "Return");
        case Mir.Jump ignored -> {
        }
        case Mir.Unreachable ignored -> {
        }
      }
    }
    definiteAssignment(function, ownedLocals, predecessors, reachable);
  }

  private void operand(Mir.Operand operand, MirNode block, int index,
                       Map<Mir.Value, Definition> definitions, Map<MirNode, Set<MirNode>> dominators) {
    require(operand != null, "Null operand in " + block);
    if (operand != Mir.Unit.INSTANCE) {
      valueType(operand.ty());
    }
    switch (operand) {
      case Mir.Value value -> {
        final var definition = definitions.get(value);
        require(definition != null, "Undefined or cross-function value in " + block);
        require(definition.block() == block ? definition.index() < index
            : dominators.get(block).contains(definition.block()),
          "Value definition does not dominate its use in " + block);
      }
      case Mir.FunctionRef reference ->
        require(functions.contains(reference.function()), "Reference to function outside module");
      case Mir.Constant constant ->
        require(constant.ty() instanceof TyValueNumber || constant.ty() instanceof TyValueBoolean
            || constant.ty() instanceof TyValueString,
          "Unsupported constant type: " + constant.ty());
      case Mir.Unit ignored -> {
      }
    }
  }

  private static void instruction(Mir.Instruction instruction, MirFunction function, Set<Mir.Local> locals) {
    switch (instruction) {
      case Mir.Binary binary -> {
        require(binary.lhs() != null && binary.rhs() != null && binary.kind() != null, "Incomplete binary instruction");
        final var type = binary.lhs().ty();
        same(type, binary.rhs().ty(), "Binary operands");
        same(binary.kind().isPredicate() ? Ty.BOOLEAN : type, binary.result().ty(), "Binary result");
        switch (binary.kind()) {
          case OR, AND -> throw new IllegalArgumentException("Invalid MIR: Logical operations must be lowered to branches");
          case BIT_AND, BIT_OR, BIT_SHIFT_LEFT, BIT_SHIFT_RIGHT ->
            require(type instanceof TyValueNumberInteger, "Bitwise operand must be an integer");
          case EQUALS, NOT_EQUALS, IS ->
            require(type instanceof TyValueNumber || type instanceof TyValueBoolean
                || type instanceof TyPointer<?> || type instanceof TyValueString,
              "Unsupported equality operand");
          default -> require(type instanceof TyValueNumber, "Arithmetic/comparison operand must be numeric");
        }
      }
      case Mir.Load load -> {
        place(load.place(), locals);
        same(load.place().ty(), load.result().ty(), "Load");
      }
      case Mir.Store store -> {
        place(store.place(), locals);
        require(store.value() != null, "Missing stored value");
        same(store.place().ty(), store.value().ty(), "Store");
      }
      case Mir.Parameter parameter -> {
        final var parameters = function.signature().parameters();
        require(parameter.index() >= 0 && parameter.index() < parameters.length, "Parameter index out of bounds");
        same(parameters[parameter.index()].ty(), parameter.result().ty(), "Parameter");
      }
      case Mir.Call call -> {
        require(call.target() != null && call.signature() != null, "Incomplete call");
        signature(call.signature());
        same(new TyPointer<>(MirTypes.functionType(call.signature())), call.target().ty(), "Call target signature");
        final var parameters = call.signature().parameters();
        require(call.arguments().size() >= parameters.length
            && (call.signature().vararg() || call.arguments().size() == parameters.length),
          "Call argument count mismatch");
        for (var i = 0; i < parameters.length; i++) {
          same(parameters[i].ty(), call.arguments().get(i).ty(), "Call argument " + i);
        }
        for (final var argument : call.arguments()) {
          valueType(argument.ty());
        }
        if (Ty.VOID.equals(call.signature().returnType())) {
          require(call.result() == null, "Void call must not define a result");
        } else {
          require(call.result() != null, "Non-void call must define a result");
          same(call.signature().returnType(), call.result().ty(), "Call result");
        }
      }
      case Mir.Convert convert -> {
        require(convert.value() != null, "Missing conversion operand");
        final var from = convert.value().ty();
        final var to = convert.result().ty();
        final boolean allowed;
        if (from instanceof TyUnion source && to instanceof TyUnion target) {
          allowed = UnionTypes.compatible(source, target);
        } else {
          allowed = sameType(from, to)
            || numericConversion(from, to)
            || from instanceof TyValueString && characterReference(to)
            || characterReference(from) && to instanceof TyValueString
            || from instanceof TyPointer<?> && to instanceof TyPointer<?>
              && (!(TupleTypes.containsTuple(from) || TupleTypes.containsTuple(to)) || TypeComparison.sameValueType(from, to));
        }
        require(allowed, "Invalid explicit conversion from " + from + " to " + to);
      }
      case Mir.UnionVariant variant -> {
        require(variant.result().ty() instanceof TyUnion, "Union construction requires a union result");
        final var types = ((TyUnion) variant.result().ty()).types();
        require(variant.variant() >= 0 && variant.variant() < types.length, "Union tag out of bounds");
        require(variant.value() != null, "Missing union payload");
        same(types[variant.variant()], variant.value().ty(), "Union payload");
      }
      case Mir.NewArray array -> {
        final var type = arrayType(array.result().ty());
        require(array.length() != null && array.length().ty() instanceof TyValueNumberInteger,
          "Array length must be an integer");
        for (final var element : array.elements()) {
          same(MirTypes.valueType(type.elementType()), element.ty(), "Array element");
        }
        require(type.size() == null || array.elements().size() <= type.size(),
          "Array initializer count exceeds its static allocation");
        if (array.length() instanceof Mir.Constant constant) {
          final var length = integerConstant(constant);
          require(length.signum() >= 0, "Array length must not be negative");
          require(type.size() == null || length.equals(BigInteger.valueOf(type.size())),
            "Array length does not match its static allocation");
          require(BigInteger.valueOf(array.elements().size()).compareTo(length) <= 0,
            "Array initializer count exceeds its requested length");
        }
      }
      case Mir.NewStruct struct -> {
        final var type = structType(struct.result().ty());
        require(type.fields().length == struct.fields().size(), "Struct field count mismatch");
        for (var i = 0; i < type.fields().length; i++) {
          same(MirTypes.valueType(type.fields()[i].ty()), struct.fields().get(i).ty(), "Struct field " + i);
        }
      }
    }
  }

  private static boolean numericConversion(Ty from, Ty to) {
    if (from instanceof TyValueNumberInteger || from instanceof TyValueBoolean) {
      return to instanceof TyValueNumberInteger || to instanceof TyValueBoolean || to instanceof TyValueNumberPrecisioned;
    }
    return from instanceof TyValueNumberPrecisioned
      && (to instanceof TyValueNumberPrecisioned || to instanceof TyValueNumberInteger);
  }

  private static boolean characterReference(Ty type) {
    return type instanceof TyPointer<?> pointer && pointer.addressSpace() == TyPointerAddressSpace.CPU
      && Ty.CHAR.equals(pointer.inner());
  }

  private static BigInteger integerConstant(Mir.Constant constant) {
    final var type = (TyValueNumberInteger) constant.ty();
    final var digits = constant.content().replace("_", "").replaceFirst("[iu]\\d+$", "")
      .replaceFirst("^([+-]?)0[xXoObB]", "$1");
    try {
      return new BigInteger(digits, type.radix());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid MIR: Invalid integer array length", exception);
    }
  }

  private static void place(Mir.Place place, Set<Mir.Local> locals) {
    require(place != null, "Missing storage place");
    valueType(place.ty());
    switch (place) {
      case Mir.Local local -> require(locals.contains(local), "Unowned or cross-function local");
      case Mir.Field field -> {
        require(field.target() != null, "Missing field target");
        final var type = structType(field.target().ty());
        require(field.index() >= 0 && field.index() < type.fields().length, "Field index out of bounds");
        same(MirTypes.valueType(type.fields()[field.index()].ty()), field.ty(), "Field place");
      }
      case Mir.Element element -> {
        require(element.target() != null && element.index() != null, "Incomplete element place");
        final var type = arrayType(element.target().ty());
        require(element.index().ty() instanceof TyValueNumberInteger, "Array index must be an integer");
        same(MirTypes.valueType(type.elementType()), element.ty(), "Element place");
      }
    }
  }

  private static TyStruct structType(Ty type) {
    require(type instanceof TyPointer<?> pointer && pointer.inner() instanceof TyStruct,
      "Expected a struct reference");
    return (TyStruct) ((TyPointer<?>) type).inner();
  }

  private static TyValueArray arrayType(Ty type) {
    require(type instanceof TyPointer<?> pointer && pointer.inner() instanceof TyValueArray,
      "Expected an array reference");
    return (TyValueArray) ((TyPointer<?>) type).inner();
  }

  private static void signature(MirFnSignature signature) {
    require(signature != null, "Missing function signature");
    concrete(signature.returnType(), identitySet());
    if (!Ty.VOID.equals(signature.returnType())) {
      valueType(signature.returnType());
    }
    for (final var parameter : signature.parameters()) {
      valueType(parameter.ty());
    }
  }

  private static void valueType(Ty type) {
    concrete(type, identitySet());
    require(!Ty.VOID.equals(type) && !(type instanceof TyStruct) && !(type instanceof TyValueArray)
        && !(type instanceof TyFn) && !(type instanceof TyOpaque),
      "Not a MIR value type: " + type);
    if (type instanceof TyUnion union) {
      for (final var variant : union.types()) {
        require(!(variant instanceof TyUnion), "Nested MIR union variant");
        if (!Ty.VOID.equals(variant)) {
          valueType(variant);
        }
      }
    }
  }

  private static void concrete(Ty type, Set<Ty> visited) {
    require(type != null, "Missing type");
    if (!visited.add(type)) {
      return;
    }
    switch (type) {
      case Ty.TyNamed ignored -> require(Ty.VOID.equals(type), "Unresolved or non-concrete MIR type: " + type);
      case TyValueNumber number -> require(number.width() != null && number.width().value() > 0,
        "Invalid numeric bit width");
      case TyValueBoolean ignored -> {
      }
      case TyValueString ignored -> {
      }
      case TyOpaque ignored -> {
      }
      case TyPointer<?> pointer -> {
        require(pointer.addressSpace() != null, "Missing pointer address space");
        concrete(pointer.inner(), visited);
      }
      case TyUnion union -> {
        for (var i = 0; i < union.types().length; i++) {
          final var variant = union.types()[i];
          concrete(variant, visited);
          for (var j = 0; j < i; j++) {
            require(!sameType(variant, union.types()[j]), "Duplicate union variant");
          }
        }
      }
      case TyStruct struct -> {
        require(struct.fields() != null, "Missing struct layout");
        for (final var field : struct.fields()) {
          require(field != null && !Ty.VOID.equals(field.ty()), "Invalid struct field");
          concrete(field.ty(), visited);
        }
      }
      case TyValueArray array -> {
        require(!Ty.VOID.equals(array.elementType()) && (array.size() == null || array.size() >= 0), "Invalid array layout");
        concrete(array.elementType(), visited);
      }
      case TyFn fn -> {
        require(fn.parameters() != null, "Missing function parameters");
        concrete(fn.returnTy(), visited);
        for (final var parameter : fn.parameters()) {
          require(parameter != null && !Ty.VOID.equals(parameter.ty()), "Invalid function parameter");
          concrete(parameter.ty(), visited);
        }
      }
      default -> throw new IllegalArgumentException("Unresolved or unsupported MIR type: " + type);
    }
  }

  private static void same(Ty expected, Ty actual, String context) {
    require(sameType(expected, actual), context + " type mismatch: expected " + expected + ", got " + actual);
  }

  private static boolean sameType(Ty a, Ty b) {
    if (a == b) {
      return true;
    }
    if (a instanceof TyUnion left && b instanceof TyUnion right) {
      return sameTypes(left.types(), right.types());
    }
    if (a instanceof TyPointer<?> left && b instanceof TyPointer<?> right) {
      return left.addressSpace() == right.addressSpace() && sameType(left.inner(), right.inner());
    }
    if (a instanceof TyFn left && b instanceof TyFn right) {
      return left.vararg() == right.vararg() && sameType(left.returnTy(), right.returnTy())
        && sameTypes(Arrays.stream(left.parameters()).map(TyParam::ty).toArray(Ty[]::new),
        Arrays.stream(right.parameters()).map(TyParam::ty).toArray(Ty[]::new));
    }
    if (a instanceof TyStruct left && b instanceof TyStruct right) {
      return Arrays.equals(Arrays.stream(left.fields()).map(TyField::name).toArray(),
        Arrays.stream(right.fields()).map(TyField::name).toArray())
        && sameTypes(Arrays.stream(left.fields()).map(TyField::ty).toArray(Ty[]::new),
        Arrays.stream(right.fields()).map(TyField::ty).toArray(Ty[]::new));
    }
    if (a instanceof TyValueArray left && b instanceof TyValueArray right) {
      return Objects.equals(left.size(), right.size()) && sameType(left.elementType(), right.elementType());
    }
    return Objects.equals(a, b);
  }

  private static boolean sameTypes(Ty[] left, Ty[] right) {
    if (left.length != right.length) {
      return false;
    }
    for (var i = 0; i < left.length; i++) {
      if (!sameType(left[i], right[i])) {
        return false;
      }
    }
    return true;
  }

  private static Set<MirNode> reachable(MirNode entry) {
    final var reachable = MirVerifier.<MirNode>identitySet();
    final var work = new ArrayDeque<MirNode>();
    work.add(entry);
    while (!work.isEmpty()) {
      final var block = work.removeFirst();
      if (reachable.add(block)) {
        work.addAll(block.successors());
      }
    }
    return reachable;
  }

  private static Map<MirNode, Set<MirNode>> dominators(MirFunction function,
                                                      Map<MirNode, Set<MirNode>> predecessors,
                                                      Set<MirNode> reachable) {
    final var dominators = new IdentityHashMap<MirNode, Set<MirNode>>();
    for (final var block : function.blocks()) {
      dominators.put(block, block == function.entry() || !reachable.contains(block)
        ? identitySet(List.of(block)) : identitySet(reachable));
    }
    boolean changed;
    do {
      changed = false;
      for (final var block : function.blocks()) {
        if (block == function.entry() || !reachable.contains(block)) {
          continue;
        }
        final var next = identitySet(reachable);
        for (final var predecessor : predecessors.get(block)) {
          if (reachable.contains(predecessor)) {
            next.retainAll(dominators.get(predecessor));
          }
        }
        next.add(block);
        if (!next.equals(dominators.get(block))) {
          dominators.put(block, next);
          changed = true;
        }
      }
    } while (changed);
    return dominators;
  }

  private static void definiteAssignment(MirFunction function, Set<Mir.Local> locals,
                                         Map<MirNode, Set<MirNode>> predecessors, Set<MirNode> reachable) {
    final var inputs = new IdentityHashMap<MirNode, Set<Mir.Local>>();
    final var outputs = new IdentityHashMap<MirNode, Set<Mir.Local>>();
    for (final var block : function.blocks()) {
      final var initial = block == function.entry() || !reachable.contains(block)
        ? MirVerifier.<Mir.Local>identitySet() : identitySet(locals);
      inputs.put(block, initial);
      outputs.put(block, initializedAfter(block, initial));
    }
    boolean changed;
    do {
      changed = false;
      for (final var block : function.blocks()) {
        if (block == function.entry() || !reachable.contains(block)) {
          continue;
        }
        final var next = identitySet(locals);
        for (final var predecessor : predecessors.get(block)) {
          if (reachable.contains(predecessor)) {
            next.retainAll(outputs.get(predecessor));
          }
        }
        final var output = initializedAfter(block, next);
        if (!next.equals(inputs.get(block)) || !output.equals(outputs.get(block))) {
          inputs.put(block, next);
          outputs.put(block, output);
          changed = true;
        }
      }
    } while (changed);
    for (final var block : function.blocks()) {
      // Unreachable blocks have no entry facts: only earlier definitions/stores in that block count.
      final var initialized = identitySet(inputs.get(block));
      for (final var instruction : block.instructions()) {
        if (instruction instanceof Mir.Load load && load.place() instanceof Mir.Local local) {
          require(initialized.contains(local), "Load of uninitialized local '" + local.name() + "' in " + block);
        }
        if (instruction instanceof Mir.Store store && store.place() instanceof Mir.Local local) {
          initialized.add(local);
        }
      }
    }
  }

  private static Set<Mir.Local> initializedAfter(MirNode block, Set<Mir.Local> input) {
    final var initialized = identitySet(input);
    for (final var instruction : block.instructions()) {
      if (instruction instanceof Mir.Store store && store.place() instanceof Mir.Local local) {
        initialized.add(local);
      }
    }
    return initialized;
  }

  private static <T> Set<T> identitySet() {
    return Collections.newSetFromMap(new IdentityHashMap<>());
  }

  private static <T> Set<T> identitySet(Collection<? extends T> values) {
    final var set = MirVerifier.<T>identitySet();
    set.addAll(values);
    return set;
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException("Invalid MIR: " + message);
    }
  }
}
