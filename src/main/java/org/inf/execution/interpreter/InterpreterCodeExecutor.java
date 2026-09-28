package org.inf.execution.interpreter;

import org.inf.exceptions.NotImplementedException;
import org.inf.execution.CodeExecutor;
import org.inf.mir.Mir;
import org.inf.mir.MirUnionValue;
import org.inf.mir.model.MirFunction;
import org.inf.mir.model.MirNode;
import org.inf.ty.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class InterpreterCodeExecutor implements CodeExecutor {

  private static final class Frame {
    final Map<Mir.Value, Object> values = new IdentityHashMap<>();
    final Map<Mir.Local, Object> locals = new IdentityHashMap<>();
    final List<Object> arguments;

    Frame(List<Object> arguments) {
      this.arguments = arguments;
    }
  }

  @Override
  public Object execute(MirNode entry) {
    return execute(entry, List.of());
  }

  private Object execute(MirNode entry, List<Object> arguments) {
    final var frame = new Frame(arguments);
    var block = entry;
    while (true) {
      for (final var instruction : block.instructions()) {
        final Object result = switch (instruction) {
          case Mir.Load load -> load(load.place(), frame);
          case Mir.Store store -> {
            store(store.place(), operand(store.value(), frame), frame);
            yield null;
          }
          case Mir.Parameter parameter -> frame.arguments.get(parameter.index());
          case Mir.Binary binary -> binary(binary, frame);
          case Mir.Convert convert -> convert(operand(convert.value(), frame), convert.result().ty());
          case Mir.UnionVariant variant -> new MirUnionValue((TyUnion) variant.result().ty(), variant.variant(), operand(variant.value(), frame));
          case Mir.Call call -> {
            final var target = (MirFunction) operand(call.target(), frame);
            if (target.external()) {
              throw new NotImplementedException("Interpreter cannot call external function: " + target.name());
            }
            yield execute(target.entry(), call.arguments().stream().map(argument -> operand(argument, frame)).toList());
          }
          case Mir.NewArray array -> {
            final var length = new BigInteger(operand(array.length(), frame).toString()).intValueExact();
            final var values = array.elements().stream().map(element -> operand(element, frame)).toArray();
            final var type = (TyValueArray) ((TyPointer<?>) array.result().ty()).inner();
            if (length < values.length || length < 0 || type.size() != null && length != type.size()) {
              throw new IllegalArgumentException("Invalid array allocation length: " + length);
            }
            final var resultArray = new Object[length];
            for (var i = 0; i < length && values.length > 0; i++) {
              resultArray[i] = values[i % values.length];
            }
            yield resultArray;
          }
          case Mir.NewStruct struct -> struct.fields().stream().map(field -> operand(field, frame)).toArray();
        };
        if (instruction.result() != null) {
          frame.values.put(instruction.result(), Objects.requireNonNull(result, "Value instruction produced no result"));
        }
      }
      switch (block.terminator()) {
        case Mir.Jump jump -> block = jump.target();
        case Mir.Branch branch -> block = (Boolean) operand(branch.predicate(), frame) ? branch.pass() : branch.fail();
        case Mir.Return ret -> {
          return operand(ret.value(), frame);
        }
        case Mir.Unreachable ignored -> throw new IllegalStateException("Reached unreachable block " + block.name());
        case null -> throw new IllegalStateException("Unterminated block " + block.name());
      }
    }
  }

  private Object operand(Mir.Operand operand, Frame frame) {
    return switch (operand) {
      case Mir.Value value -> Objects.requireNonNull(frame.values.get(value), "Value used before definition: " + value);
      case Mir.Constant constant -> constant(constant);
      case Mir.FunctionRef reference -> reference.function();
      case Mir.Unit ignored -> null;
    };
  }

  private Object load(Mir.Place place, Frame frame) {
    final var result = switch (place) {
      case Mir.Local local -> frame.locals.get(local);
      case Mir.Field field -> ((Object[]) operand(field.target(), frame))[field.index()];
      case Mir.Element element -> ((Object[]) operand(element.target(), frame))[((Number) operand(element.index(), frame)).intValue()];
    };
    return Objects.requireNonNull(result, "Read of uninitialized storage: " + place);
  }

  private void store(Mir.Place place, Object value, Frame frame) {
    switch (place) {
      case Mir.Local local -> frame.locals.put(local, value);
      case Mir.Field field -> ((Object[]) operand(field.target(), frame))[field.index()] = value;
      case Mir.Element element -> ((Object[]) operand(element.target(), frame))[((Number) operand(element.index(), frame)).intValue()] = value;
    }
  }

  private Object constant(Mir.Constant constant) {
    return switch (constant.ty()) {
      case TyValueString ignored -> constant.content();
      case TyValueBoolean ignored -> Boolean.parseBoolean(constant.content());
      case TyValueNumberInteger integer -> number(new BigDecimal(new BigInteger(constant.content(), integer.radix())), integer);
      case TyValueNumberPrecisioned real -> number(new BigDecimal(constant.content()), real);
      default -> throw new NotImplementedException("Unsupported constant type: " + constant.ty());
    };
  }

  private Object convert(Object value, Ty expected) {
    if (expected instanceof TyUnion union && value instanceof MirUnionValue old) {
      final var variantType = old.type().types()[old.variant()];
      for (var i = 0; i < union.types().length; i++) {
        if (variantType.equals(union.types()[i])) {
          return new MirUnionValue(union, i, old.payload());
        }
      }
      throw new IllegalArgumentException("Union conversion loses variant " + variantType);
    }
    if (value instanceof Number number && expected instanceof TyValueNumber) {
      return number(new BigDecimal(number.toString()), expected);
    }
    if (value instanceof Boolean bool && expected instanceof TyValueNumberInteger) {
      return number(BigDecimal.valueOf(bool ? 1 : 0), expected);
    }
    if (expected instanceof TyPointer<?>) {
      return value;
    }
    throw new NotImplementedException("Unsupported interpreter conversion to " + expected);
  }

  private Object binary(Mir.Binary binary, Frame frame) {
    final var lhs = operand(binary.lhs(), frame);
    final var rhs = operand(binary.rhs(), frame);
    if (binary.kind() == org.inf.mir.model.MirBinaryOperationKind.EQUALS || binary.kind() == org.inf.mir.model.MirBinaryOperationKind.IS) {
      return lhs instanceof Number a && rhs instanceof Number b
        ? new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0 : Objects.equals(lhs, rhs);
    }
    if (binary.kind() == org.inf.mir.model.MirBinaryOperationKind.NOT_EQUALS) {
      return !Objects.equals(lhs, rhs);
    }
    if (!(lhs instanceof Number a) || !(rhs instanceof Number b)) {
      throw new NotImplementedException("Non-numeric binary operation: " + binary.kind());
    }
    final var left = new BigDecimal(a.toString());
    final var right = new BigDecimal(b.toString());
    final var comparison = left.compareTo(right);
    switch (binary.kind()) {
      case LT -> { return comparison < 0; }
      case LTE -> { return comparison <= 0; }
      case GT -> { return comparison > 0; }
      case GTE -> { return comparison >= 0; }
      default -> { }
    }
    final var precision = binary.result().ty() instanceof TyValueNumberPrecisioned real ? real.precision() : 0;
    final var result = switch (binary.kind()) {
      case ADD -> left.add(right);
      case SUBTRACT -> left.subtract(right);
      case MULTIPLY -> left.multiply(right);
      case DIVIDE -> left.divide(right, precision, RoundingMode.HALF_DOWN);
      case REMAINDER, MODULUS -> left.remainder(right);
      case BIT_AND -> new BigDecimal(left.toBigInteger().and(right.toBigInteger()));
      case BIT_OR -> new BigDecimal(left.toBigInteger().or(right.toBigInteger()));
      case BIT_SHIFT_LEFT -> new BigDecimal(left.toBigInteger().shiftLeft(right.intValue()));
      case BIT_SHIFT_RIGHT -> new BigDecimal(left.toBigInteger().shiftRight(right.intValue()));
      default -> throw new NotImplementedException("Unsupported interpreter binary operation: " + binary.kind());
    };
    return number(result, binary.result().ty());
  }

  private Number number(BigDecimal value, Ty type) {
    return switch (type) {
      case TyValueNumberInteger integer -> {
        final var width = integer.width().value();
        final var modulus = BigInteger.ONE.shiftLeft(width);
        var normalized = value.toBigInteger().mod(modulus);
        if (integer.signed() && normalized.testBit(width - 1)) {
          normalized = normalized.subtract(modulus);
        }
        if (width < 32 || width == 32 && integer.signed()) {
          yield normalized.intValue();
        }
        if (width < 64 || width == 64 && integer.signed()) {
          yield normalized.longValue();
        }
        yield normalized;
      }
      case TyValueNumberPrecisioned real when real.kind() == RealKind.FLOAT -> value.floatValue();
      case TyValueNumberPrecisioned ignored -> value.doubleValue();
      default -> throw new NotImplementedException("Unsupported interpreter number: " + type);
    };
  }
}
