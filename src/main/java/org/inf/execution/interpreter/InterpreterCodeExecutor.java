package org.inf.execution.interpreter;

import org.inf.exceptions.NotImplementedException;
import org.inf.exceptions.UnexpectedExpressionException;
import org.inf.execution.CodeExecutor;
import org.inf.mir.Mir;
import org.inf.mir.model.MirNode;
import org.inf.ty.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class InterpreterCodeExecutor implements CodeExecutor {

  @Override
  public Object execute(final MirNode node) {

    final var scopes = new ArrayList<Scope>();
    scopes.add(new Scope());

    return this.execute(node, scopes);
  }

  public Object execute(final MirNode node, final List<Scope> scopes) {

    for (final var instr : node.instructions()) {

      switch (instr) {
        case final Mir.InstrCreateLiteral lit -> {
          final var javaValue = this.lower_literal(lit);
          final var valueId = (long) lit.hashCode();
          final var scope = scopes.getLast();
          scope.valueMap.put(valueId, javaValue);
        }
        case final Mir.InstrStore store -> {
          final var i = 0;
        }
        case final Mir.InstrBinaryOperation bin -> {

          final var lhsId = (long) bin.lhs().hashCode();
          final var rhsId = (long) bin.rhs().hashCode();
          final var scope = scopes.getLast();

          final var lhsValue = scope.valueMap.get(lhsId);
          final var rhsValue = scope.valueMap.get(rhsId);

          if (lhsValue instanceof final Number lhsNumber && rhsValue instanceof final Number rhsNumber) {

            final var lhsBd = new BigDecimal(lhsNumber.toString());
            final var rhsBd = new BigDecimal(rhsNumber.toString());

            final var scale = bin.ty() instanceof final TyValueNumberPrecisioned np
              ? np.precision()
              : 0;

            final var resBd = switch (bin.kind()) {
              case ADD -> lhsBd.add(rhsBd);
              case SUBTRACT -> lhsBd.subtract(rhsBd);
              case MULTIPLY -> lhsBd.multiply(rhsBd);
              case DIVIDE -> lhsBd.divide(rhsBd, scale, RoundingMode.HALF_DOWN);
              default -> throw new NotImplementedException("Not yet implemented " + rhsValue);
            };

            final Number res = switch (bin.ty()) {
              case final TyValueNumberInteger tyi -> {
                if (tyi.width().value() > Ty.INTEGER.width().value()) {
                  yield resBd.longValue();
                } else if (tyi.width().value() <= Ty.SHORT.width().value()) {
                  yield resBd.shortValue();
                } else {
                  yield resBd.intValue();
                }
              }
              case final TyValueNumberPrecisioned typ -> switch (typ.kind()) {
                case RealKind.FLOAT -> resBd.floatValue();
                case RealKind.DOUBLE -> resBd.doubleValue();
              };
              default -> throw new NotImplementedException("Not implemented type conversion to " + bin.ty());
            };

            final var id = (long) bin.hashCode();
            scope.valueMap.put(id, res);
          } else {
            throw new NotImplementedException("Not yet implemented " + rhsValue);
          }
        }
        case final Mir.InstrReturn ret -> {

          final var id = (long) ret.instr().hashCode();
          final var scope = scopes.getLast();

          scope.result = scope.valueMap.get(id);
        }
        case final Mir.InstrCreateFn fn -> {

          final var id = (long) fn.hashCode();
          final var scope = scopes.getLast();

          scope.functions.put(id, fn);
        }
        case final Mir.InstrCall call -> {

          // TODO: Make sure there is a difference in the instructions between `val fn = () => ...` and `var fn = () => ...`
          //        Since for the first the function will never move, so we can just reference the function directly
          //        But for `var` the function could in theory be replaced, so it needs to be stored as a pointer to a function!
          // TODO: Create a test case where we replace the function reference between calls, and make sure things work properly!

          // TODO: Do the actual call...

          if (call.target() instanceof final Mir.InstrCreateFn fn) {

            final var scope = scopes.getLast();
            final var arguments = call.arguments();

            try {

              for (int i = 0, argumentsLength = arguments.length; i < argumentsLength; i++) {
                final var argument = arguments[i];
                var name = argument.name();
                if (name == null) {
                  name = fn.signature().parameters()[i].name();
                }

                scope.valueMap.put((long) name.hashCode(), argument.instruction());
              }

              return this.execute(fn.entry(), scopes);

            } finally {
              for (int i = 0, argumentsLength = arguments.length; i < argumentsLength; i++) {
                final var argument = arguments[i];
                var name = argument.name();
                if (name == null) {
                  name = fn.signature().parameters()[i].name();
                }

                scope.valueMap.remove((long) name.hashCode());
              }
            }

          } else {
            throw new IllegalArgumentException("Do not know how to handle fn target: " + call.target());
          }

        }
        case final Mir.InstrGetParam param -> {

          final var hash = (long) param.parameter().name().hashCode();
          for (var i = scopes.size() - 1; i >= 0; i--) {

            final var scope = scopes.get(i);
            if (scope.valueMap.containsKey(hash)) {
              return scope.valueMap.get(hash);
            }
          }

          throw new IllegalArgumentException("Could not find argument value for parameter: " + param.parameter().name());
        }
        default -> throw new IllegalArgumentException("Unexpected instruction: " + instr + " (" + instr.getClass().getName() + ")");
      }
    }

    return scopes.getLast().result;
  }

  private Object lower_literal(final Mir.InstrCreateLiteral literal) {

    return switch (literal.ty()) {
      case final TyValueString str -> literal.content();
      case final TyValueNumberInteger ni -> {
        if (ni.width().value() == 64) {
          yield Long.parseLong(literal.content(), ni.radix());
        } else {
          yield Integer.parseInt(literal.content(), ni.radix());
        }
      }
      case final TyValueNumberPrecisioned np -> switch (np.kind()) {
        case FLOAT -> Float.parseFloat(literal.content());
        case DOUBLE -> Double.parseDouble(literal.content());
      };
      case final TyValueBoolean b -> Boolean.parseBoolean(literal.content());
      default -> throw new UnexpectedExpressionException(literal);
    };
  }
}
