package org.inf.mir;

import org.inf.exceptions.NotImplementedException;
import org.inf.exceptions.UnreachableCodeException;
import org.inf.hir.Hir;
import org.inf.hir.HirArgumentBinding;
import org.inf.hir.HirCallArguments;
import org.inf.hir.HirSpreadShape;
import org.inf.hir.HirTupleAccess;
import org.inf.hir.HirTupleMatching;
import org.inf.hir.HirVisitor;
import org.inf.mir.model.*;
import org.inf.thir.raising.ThirRaiseResult;
import org.inf.ty.*;
import org.inf.ty.util.Tys;
import org.inf.ty.util.TupleTypes;
import org.inf.ty.util.TypeComparison;

import java.util.*;

/** Lowers evaluation order and structured control flow into explicit values, places and basic blocks. */
public final class ThirToMirLowering {

  private sealed interface Flow permits Continues, Diverges {
  }

  private record Continues(Mir.Operand value) implements Flow {
  }

  private enum Diverges implements Flow {
    INSTANCE
  }

  private static final Continues UNIT = new Continues(Mir.Unit.INSTANCE);

  private static final class ModuleBuilder {
    final List<Hir.FunctionSignature> signatures = new ArrayList<>();
    final Map<Hir.FunctionSignature, Hir.Function> definitions = new IdentityHashMap<>();
    final Map<Hir.FunctionSignature, String> names = new IdentityHashMap<>();
    final Map<Hir.FunctionSignature, MirFunction> functions = new IdentityHashMap<>();
    final Map<Hir.Expression, Hir.FunctionSignature> staticBindings = new IdentityHashMap<>();
    final List<MirFunction> module = new ArrayList<>();

    void collect(Hir.Expression root) {
      root.visit(new HirVisitor() {
        @Override
        public void visitAssignment(Hir.Assignment assignment) {
          if (assignment.lhs() instanceof Hir.Dec declaration) {
            final var signature = switch (assignment.rhs()) {
              case Hir.Function function -> function.signature();
              case Hir.FunctionSignature external -> external;
              default -> null;
            };
            if (signature != null) {
              names.put(signature, declaration.lexeme().name());
              if (declaration.mutabilityKind() != Hir.MutabilityKind.MUTABLE) {
                staticBindings.put(declaration, signature);
              }
            }
          }
          HirVisitor.super.visitAssignment(assignment);
        }

        @Override
        public void visitFunction(Hir.Function function) {
          definitions.put(function.signature(), function);
          HirVisitor.super.visitFunction(function);
        }

        @Override
        public void visitFunctionSignature(Hir.FunctionSignature signature) {
          if (signatures.stream().noneMatch(existing -> existing == signature)) {
            signatures.add(signature);
          }
        }
      });

      for (final var signature : signatures) {
        final var external = !definitions.containsKey(signature);
        final var sourceName = names.getOrDefault(signature, "lambda");
        final var name = external ? sourceName : sourceName + "$" + module.size();
        final var function = new MirFunction(name, MirTypes.signature(signature.ty()), external);
        if (!external && signature.vararg()) {
          throw new org.inf.exceptions.InvalidImplementationException("Language does not support implementing your own vararg-receiving functions");
        }
        functions.put(signature, function);
        module.add(function);
      }
    }
  }

  private static final class LoopTargets {
    final MirNode header;
    final MirNode exit;
    boolean hasBreak;

    LoopTargets(MirNode header, MirNode exit) {
      this.header = header;
      this.exit = exit;
    }
  }

  private final ModuleBuilder module;
  private final MirFunction function;
  private final Map<Hir.Expression, Mir.Local> locals = new IdentityHashMap<>();
  private final Deque<LoopTargets> loops = new ArrayDeque<>();
  private MirNode current;

  private ThirToMirLowering(ModuleBuilder module, MirFunction function) {
    this.module = module;
    this.function = function;
    current = function.entry();
  }

  public static MirLoweringResult lower(ThirRaiseResult thir) {
    final var module = new ModuleBuilder();
    module.collect(thir.root());
    final var scriptTy = MirTypes.returnType(thir.root().ty());
    final var script = new MirFunction("main", new MirFnSignature(new MirFnParameter[0], false, scriptTy), false);
    module.module.addFirst(script);

    for (final var signature : module.signatures) {
      final var definition = module.definitions.get(signature);
      if (definition != null) {
        final var lowering = new ThirToMirLowering(module, module.functions.get(signature));
        lowering.parameters(signature);
        lowering.finish(definition.body());
      }
    }
    new ThirToMirLowering(module, script).finish(thir.root());
    final var result = new MirLoweringResult(script, module.module);
    MirVerifier.verify(result);
    return result;
  }

  private void parameters(Hir.FunctionSignature signature) {
    for (var i = 0; i < signature.parameters().length; i++) {
      final var source = signature.parameters()[i];
      final var type = function.signature().parameters()[i].ty();
      final var value = function.newValue(type);
      emit(new Mir.Parameter(value, i));
      final var local = function.newLocal(source.lexeme().name(), type);
      locals.put(source, local);
      emit(new Mir.Store(local, value));
    }
  }

  private void finish(Hir.Expression body) {
    final var flow = lower(body);
    if (flow instanceof Continues continued) {
      terminate(new Mir.Return(convert(continued.value(), function.signature().returnType())));
    }
  }

  private void emit(Mir.Instruction instruction) {
    if (current == null) {
      throw new IllegalStateException("Cannot emit instructions without a continuation");
    }
    current.append(instruction);
  }

  private void terminate(Mir.Terminator terminator) {
    current.terminate(terminator);
    current = null;
  }

  private Flow lower(Hir.Expression expression) {
    if (current == null) {
      throw new UnreachableCodeException("Unreachable expression: " + expression);
    }
    return switch (expression) {
      case Hir.Program program -> lower(program.expressions());
      case Hir.Expressions expressions -> expressions(expressions.children());
      case Hir.Block block -> lower(block.children());
      case Hir.Literal literal -> new Continues(new Mir.Constant(literal.content(), literal.ty()));
      case Hir.Return ret -> returnValue(ret);
      case Hir.Conditional conditional -> conditional(conditional);
      case Hir.Loop loop -> loop(loop);
      case Hir.LoopBreak exit -> breakLoop(exit);
      case Hir.LoopContinue _ -> continueLoop();
      case Hir.Identifier identifier -> identifier(identifier);
      case Hir.Assignment assignment -> assignment(assignment);
      case Hir.CompoundAssignment assignment -> compoundAssignment(assignment);
      case Hir.Dec declaration -> declaration(declaration);
      case Hir.Function defined -> new Continues(functionReference(defined.signature()));
      case Hir.FunctionSignature signature -> new Continues(functionReference(signature));
      case Hir.Call call -> call(call);
      case Hir.BinaryOperation binary -> binary(binary);
      case Hir.Convert conversion -> conversion(conversion);
      case Hir.Not not -> not(not);
      case Hir.Array array -> array(array);
      case Hir.ArrayAccess access -> arrayAccess(access);
      case Hir.Path path -> path(path);
      case Hir.NewByBlock instance -> instance(instance);
      case Hir.Struct _ -> UNIT;
      case Hir.TyExpr _ -> UNIT;
      case Hir.NewByCtor _ -> throw new NotImplementedException("Constructor calls are not yet normalized by THIR");
      case Hir.Tuple tuple -> tuple(tuple);
      default -> throw new NotImplementedException("Unsupported typed expression: " + expression.getClass().getSimpleName());
    };
  }

  private Flow expressions(Hir.Expression[] expressions) {
    Flow flow = UNIT;
    for (final var expression : expressions) {
      if (current == null && expression instanceof Hir.Assignment assignment
        && assignment.lhs() instanceof Hir.Dec declaration
        && (module.staticBindings.containsKey(declaration) || assignment.rhs() instanceof Hir.Struct || assignment.rhs() instanceof Hir.TyExpr)) {
        continue;
      }
      flow = lower(expression);
    }
    return flow;
  }

  private Mir.FunctionRef functionReference(Hir.FunctionSignature signature) {
    return new Mir.FunctionRef(Objects.requireNonNull(module.functions.get(signature), "Function was not declared"));
  }

  private Flow returnValue(Hir.Return ret) {
    final var flow = lower(ret.expression());
    if (flow instanceof Continues continued) {
      terminate(new Mir.Return(convert(continued.value(), function.signature().returnType())));
    }
    return Diverges.INSTANCE;
  }

  private Flow conversion(Hir.Convert conversion) {
    final var flow = lower(conversion.expression());
    if (flow instanceof Continues continued) {
      return new Continues(convert(continued.value(), MirTypes.valueType(conversion.targetTy())));
    }
    return flow;
  }

  private Flow declaration(Hir.Dec declaration) {
    locals.computeIfAbsent(declaration, key -> function.newLocal(declaration.lexeme().name(), MirTypes.valueType(declaration.valueTy())));
    return UNIT;
  }

  private Flow identifier(Hir.Identifier identifier) {
    final var declaration = identifier.target();
    if (declaration instanceof Hir.Function defined) {
      return new Continues(functionReference(defined.signature()));
    }
    if (declaration instanceof Hir.FunctionSignature signature) {
      return new Continues(functionReference(signature));
    }
    final var signature = module.staticBindings.get(declaration);
    if (signature != null) {
      return new Continues(functionReference(signature));
    }
    final var local = locals.get(declaration);
    if (local == null) {
      throw new IllegalArgumentException("No local for resolved declaration of '" + identifier.lexeme().name()
        + "'; nonlocal captures must be lowered to parameters before MIR");
    }
    return new Continues(load(local));
  }

  private Mir.Value load(Mir.Place place) {
    final var value = function.newValue(place.ty());
    emit(new Mir.Load(value, place));
    return value;
  }

  private Flow assignment(Hir.Assignment assignment) {
    if (assignment.lhs() instanceof Hir.Dec declaration) {
      if (assignment.rhs() instanceof Hir.Struct || assignment.rhs() instanceof Hir.TyExpr) {
        return UNIT;
      }
      final var rhs = lower(assignment.rhs());
      if (rhs instanceof Diverges) {
        return rhs;
      }
      if (module.staticBindings.containsKey(declaration)) {
        return UNIT;
      }
      final var value = ((Continues) rhs).value();
      final var type = MirTypes.valueType(declaration.valueTy());
      final var local = function.newLocal(declaration.lexeme().name(), type);
      locals.put(declaration, local);
      emit(new Mir.Store(local, convert(value, type)));
      return UNIT;
    }

    final var place = place(assignment.lhs());
    if (current == null) {
      return Diverges.INSTANCE;
    }
    final var rhs = lower(assignment.rhs());
    if (rhs instanceof Continues continued) {
      emit(new Mir.Store(place, convert(continued.value(), place.ty())));
      return UNIT;
    }
    return rhs;
  }

  private Mir.Place place(Hir.Expression expression) {
    return switch (expression) {
      case Hir.Identifier identifier -> {
        final var local = locals.get(identifier.target());
        if (local == null) {
          throw new IllegalArgumentException("Assignment has no mutable local: " + identifier);
        }
        yield local;
      }
      case Hir.Path path -> pathPlace(path);
      case Hir.ArrayAccess access -> {
        final var target = lower(access.target());
        if (target instanceof Diverges) {
          yield null;
        }
        final var operand = ((Continues) target).value();
        if (MirTypes.pointee(operand.ty()) instanceof TyStruct tuple && tuple.tuple()) {
          final var index = HirTupleAccess.index(tuple, access.accessor());
          yield new Mir.Field(operand, index, MirTypes.valueType(tuple.fields()[index].ty()));
        }
        final var index = lower(access.accessor());
        if (index instanceof Diverges) {
          yield null;
        }
        yield new Mir.Element(operand, ((Continues) index).value(), MirTypes.valueType(access.ty()));
      }
      default -> throw new IllegalArgumentException("Not an assignable place: " + expression);
    };
  }

  private Flow compoundAssignment(Hir.CompoundAssignment assignment) {
    final var place = place(assignment.target());
    if (current == null) {
      return Diverges.INSTANCE;
    }
    final var lhs = load(place);
    final var rhs = lower(assignment.rhs());
    if (rhs instanceof Diverges) {
      return rhs;
    }
    final var value = binary(lhs, assignment.kind(), ((Continues) rhs).value());
    emit(new Mir.Store(place, convert(value, place.ty())));
    return UNIT;
  }

  private Flow path(Hir.Path path) {
    final var place = pathPlace(path);
    return current == null ? Diverges.INSTANCE : new Continues(load(place));
  }

  private Mir.Place pathPlace(Hir.Path path) {
    final var elements = path.elements();
    if (elements.length < 2) {
      throw new IllegalArgumentException("A field path requires a target and a field");
    }
    final var target = lower(elements[0]);
    if (target instanceof Diverges) {
      return null;
    }
    var operand = ((Continues) target).value();
    Mir.Place place = null;
    for (var i = 1; i < elements.length; i++) {
      final var aggregate = MirTypes.pointee(operand.ty());
      if (!(aggregate instanceof TyStruct struct)) {
        throw new IllegalArgumentException("Field access requires a struct reference: " + aggregate);
      }
      final var index = fieldIndex(struct, fieldName(elements[i]));
      place = new Mir.Field(operand, index, MirTypes.valueType(struct.fields()[index].ty()));
      if (i < elements.length - 1) {
        operand = load(place);
      }
    }
    return place;
  }

  private static String fieldName(Hir.Expression expression) {
    return switch (expression) {
      case Hir.Identifier identifier -> identifier.lexeme().name();
      case Hir.Lexeme lexeme -> lexeme.name();
      default -> throw new IllegalArgumentException("Expected field name: " + expression);
    };
  }

  private static int fieldIndex(TyStruct struct, String name) {
    final var fields = struct.fields();
    for (var i = 0; i < fields.length; i++) {
      if (name.equals(fields[i].name())) {
        return i;
      }
    }
    throw new IllegalArgumentException("Unknown struct field: " + name);
  }

  private Flow conditional(Hir.Conditional conditional) {
    final var predicate = lower(conditional.predicate());
    if (predicate instanceof Diverges) {
      return predicate;
    }
    final var pass = function.newBlock("if_true");
    final var fail = function.newBlock("if_false");
    terminate(new Mir.Branch(((Continues) predicate).value(), pass, fail));

    current = pass;
    final var passFlow = lower(conditional.pass());
    final var passExit = current;
    current = fail;
    final var failFlow = conditional.fail() == null ? UNIT : lower(conditional.fail());
    final var failExit = current;
    if (passFlow instanceof Diverges && failFlow instanceof Diverges) {
      current = null;
      return Diverges.INSTANCE;
    }

    final var merge = function.newBlock("if_merge");
    final var resultTypes = new ArrayList<Ty>();
    if (passFlow instanceof Continues value) {
      resultTypes.add(value.value().ty());
    }
    if (failFlow instanceof Continues value) {
      resultTypes.add(value.value().ty());
    }
    final var resultType = Tys.union(resultTypes.toArray(Ty[]::new));
    final var result = resultType == Ty.VOID ? null : function.newLocal("if_result", resultType);
    connect(passExit, passFlow, result, merge);
    connect(failExit, failFlow, result, merge);
    current = merge;
    return result == null ? UNIT : new Continues(load(result));
  }

  private void connect(MirNode exit, Flow flow, Mir.Local result, MirNode merge) {
    if (flow instanceof Continues value) {
      current = Objects.requireNonNull(exit);
      if (result != null) {
        emit(new Mir.Store(result, convert(value.value(), result.ty())));
      }
      terminate(new Mir.Jump(merge));
    }
  }

  private Flow loop(Hir.Loop loop) {
    final var header = function.newBlock("loop");
    final var exit = function.newBlock("loop_exit");
    terminate(new Mir.Jump(header));
    final var targets = new LoopTargets(header, exit);
    loops.push(targets);
    current = header;
    try {
      final var flow = lower(loop.body());
      if (flow instanceof Continues) {
        terminate(new Mir.Jump(header));
      }
    } finally {
      loops.pop();
    }
    if (!targets.hasBreak) {
      exit.terminate(new Mir.Unreachable());
      current = null;
      return Diverges.INSTANCE;
    }
    current = exit;
    return UNIT;
  }

  private Flow breakLoop(Hir.LoopBreak exit) {
    if (loops.isEmpty()) {
      throw new IllegalArgumentException("Break outside a loop");
    }
    if (exit.value() != null) {
      throw new NotImplementedException("Value-carrying breaks require typed loop results");
    }
    final var targets = loops.peek();
    targets.hasBreak = true;
    terminate(new Mir.Jump(targets.exit));
    return Diverges.INSTANCE;
  }

  private Flow continueLoop() {
    if (loops.isEmpty()) {
      throw new IllegalArgumentException("Continue outside a loop");
    }
    terminate(new Mir.Jump(loops.peek().header));
    return Diverges.INSTANCE;
  }

  private Flow binary(Hir.BinaryOperation binary) {
    if (binary.kind().isShortCircuiting()) {
      final var constant = new Hir.Literal(Boolean.toString(binary.kind() == Hir.BinaryOperationKind.OR), Ty.BOOLEAN);
      return conditional(new Hir.Conditional(
        binary.lhs(),
        binary.kind() == Hir.BinaryOperationKind.AND ? binary.rhs() : constant,
        binary.kind() == Hir.BinaryOperationKind.AND ? constant : binary.rhs(),
        Ty.BOOLEAN,
        null
      ));
    }
    final var left = lower(binary.lhs());
    if (left instanceof Diverges) {
      return left;
    }
    final var right = lower(binary.rhs());
    if (right instanceof Diverges) {
      return right;
    }
    return new Continues(binary(((Continues) left).value(), binary.kind(), ((Continues) right).value()));
  }

  private Mir.Value binary(Mir.Operand lhs, Hir.BinaryOperationKind operation, Mir.Operand rhs) {
    final var kind = MirBinaryOperationKind.ofHirKind(operation);
    final var common = lhs.ty().equals(rhs.ty()) ? lhs.ty() : Tys.getCommonDenominator(lhs.ty(), rhs.ty()).ty();
    if (common == null) {
      throw new IllegalArgumentException(
        "Binary operands have incompatible types: %s and %s".formatted(lhs.ty(), rhs.ty())
      );
    }
    lhs = convert(lhs, common);
    rhs = convert(rhs, common);
    final var result = function.newValue(kind.isPredicate() ? Ty.BOOLEAN : common);
    emit(new Mir.Binary(result, lhs, kind, rhs));
    return result;
  }

  private Flow not(Hir.Not not) {
    final var operand = lower(not.expression());
    if (operand instanceof Diverges) {
      return operand;
    }
    final var result = function.newValue(Ty.BOOLEAN);
    emit(new Mir.Binary(result, ((Continues) operand).value(), MirBinaryOperationKind.EQUALS, new Mir.Constant("false", Ty.BOOLEAN)));
    return new Continues(result);
  }

  private Flow call(Hir.Call call) {
    final var targetFlow = lower(call.target());
    if (targetFlow instanceof Diverges) {
      return targetFlow;
    }
    final var target = ((Continues) targetFlow).value();
    if (!(MirTypes.pointee(target.ty()) instanceof TyFn type)) {
      throw new IllegalArgumentException("Call target is not a function: " + target.ty());
    }
    final var signature = new MirFnSignature(Arrays.stream(type.parameters())
      .map(p -> new MirFnParameter(p.name(), p.ty())).toArray(MirFnParameter[]::new), type.vararg(), type.returnTy());
    final var parameters = signature.parameters();
    final var entries = call.arguments();
    final var count = HirCallArguments.count(entries);
    final var arguments = new Mir.Operand[Math.max(parameters.length, count)];
    final var binding = new HirArgumentBinding(
      Arrays.stream(parameters).map(MirFnParameter::name).toArray(String[]::new),
      signature.vararg(), count
    );
    for (final var argument : entries) {
      final var spread = argument.value() instanceof Hir.Spread value ? value : null;
      final var flow = lower(spread == null ? argument.value() : spread.value());
      if (flow instanceof Diverges) {
        return flow;
      }
      final var indices = HirCallArguments.bind(argument, binding);
      final var operand = ((Continues) flow).value();
      for (var i = 0; i < indices.length; i++) {
        final var index = indices[i];
        if (index < 0) {
          continue;
        }
        final var value = spread == null ? operand
          : load(new Mir.Field(operand, i, MirTypes.valueType(HirSpreadShape.fields(spread)[i].ty())));
        if (index < parameters.length) {
          arguments[index] = convert(value, parameters[index].ty());
        } else {
          arguments[index] = promoteVararg(value);
        }
      }
    }
    binding.requireComplete();
    final var result = signature.returnType() == Ty.VOID ? null : function.newValue(signature.returnType());
    emit(new Mir.Call(result, target, signature, Arrays.asList(arguments).subList(0, binding.argumentCount())));
    if (call.ty() == Ty.DEADEND) {
      terminate(new Mir.Unreachable());
      return Diverges.INSTANCE;
    }
    return result == null ? UNIT : new Continues(result);
  }

  private Mir.Operand promoteVararg(Mir.Operand value) {
    if (value.ty() instanceof TyValueNumberInteger integer && integer.width().value() < 32 || value.ty() == Ty.BOOLEAN) {
      return convert(value, Ty.INTEGER);
    }
    if (value.ty() instanceof TyValueNumberPrecisioned real && real.width().value() < 64) {
      return convert(value, Ty.DOUBLE);
    }
    return value;
  }

  private Flow array(Hir.Array array) {
    final var type = (TyValueArray) array.ty();
    final var elements = new ArrayList<Mir.Operand>();
    for (final var expression : array.elements()) {
      final var flow = lower(expression);
      if (flow instanceof Diverges) {
        return flow;
      }
      elements.add(convert(((Continues) flow).value(), MirTypes.valueType(type.elementType())));
    }
    Mir.Operand length;
    if (array.length() == null) {
      length = new Mir.Constant(Integer.toString(type.size() == null ? elements.size() : type.size()), Ty.INTEGER);
    } else {
      final var flow = lower(array.length());
      if (flow instanceof Diverges) {
        return flow;
      }
      length = ((Continues) flow).value();
    }
    final var result = function.newValue(MirTypes.valueType(type));
    emit(new Mir.NewArray(result, elements, length));
    return new Continues(result);
  }

  private Flow arrayAccess(Hir.ArrayAccess access) {
    final var place = place(access);
    return current == null ? Diverges.INSTANCE : new Continues(load(place));
  }

  private Flow tuple(Hir.Tuple tuple) {
    final var entries = tuple.children();
    final var fields = new Mir.Operand[entries.length];
    final var slots = tuple.contextualType() == null
      ? null
      : HirTupleMatching.match(tuple, tuple.contextualType());
    for (var i = 0; i < entries.length; i++) {
      final var entry = entries[i];
      final var flow = lower(entry.value());
      if (flow instanceof Diverges) {
        return flow;
      }
      fields[slots == null ? i : slots[i]] = ((Continues) flow).value();
    }
    final var result = function.newValue(MirTypes.valueType(tuple.ty()));
    emit(new Mir.NewStruct(result, Arrays.asList(fields)));
    return new Continues(result);
  }

  private Flow instance(Hir.NewByBlock instance) {
    final var type = (TyStruct) instance.ty();
    final var fields = new Mir.Operand[type.fields().length];
    for (final var assignment : instance.fields()) {
      final var index = fieldIndex(type, fieldName(assignment.lhs()));
      if (fields[index] != null) {
        throw new IllegalArgumentException("Duplicate initializer for field " + type.fields()[index].name());
      }
      final var flow = lower(assignment.rhs());
      if (flow instanceof Diverges) {
        return flow;
      }
      fields[index] = convert(((Continues) flow).value(), MirTypes.valueType(type.fields()[index].ty()));
    }
    if (Arrays.stream(fields).anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Every struct field must be initialized");
    }
    final var result = function.newValue(MirTypes.valueType(type));
    emit(new Mir.NewStruct(result, Arrays.asList(fields)));
    return new Continues(result);
  }

  private Mir.Operand convert(Mir.Operand operand, Ty expected) {
    if (operand.ty().equals(expected)) {
      return operand;
    }
    final var result = function.newValue(expected);
    if (expected instanceof TyUnion union && !(operand.ty() instanceof TyUnion)) {
      for (var i = 0; i < union.types().length; i++) {
        final var variant = union.types()[i];
        if (variant.equals(operand.ty()) || TypeComparison.sameValueType(variant, operand.ty())) {
          emit(new Mir.UnionVariant(result, i, convert(operand, variant)));
          return result;
        }
      }
      throw new IllegalArgumentException("Value " + operand.ty() + " is not a variant of " + expected);
    }
    if (operand.ty() == Ty.VOID || expected == Ty.VOID || expected == Ty.DEADEND) {
      throw new IllegalArgumentException("Cannot convert " + operand.ty() + " to " + expected);
    }
    if (operand.ty() instanceof TyPointer<?> actualPointer && actualPointer.inner() instanceof TyFn actualFunction
      && expected instanceof TyPointer<?> expectedPointer && expectedPointer.inner() instanceof TyFn expectedFunction) {
      if (!TypeComparison.sameValueType(actualFunction, expectedFunction)) {
        throw new IllegalArgumentException("Cannot convert incompatible function signatures: %s to %s".formatted(actualFunction, expectedFunction));
      }
    }
    if ((TupleTypes.containsTuple(operand.ty()) || TupleTypes.containsTuple(expected))
      && !TypeComparison.sameValueType(operand.ty(), expected)) {
      throw new IllegalArgumentException("Cannot reinterpret incompatible tuple layouts: " + operand.ty() + " to " + expected);
    }
    emit(new Mir.Convert(result, operand));
    return result;
  }
}
