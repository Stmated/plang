package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirArgumentBinding;
import org.inf.hir.HirCallArguments;
import org.inf.hir.HirSpreadShape;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyParam;
import org.inf.ty.TyStruct;
import org.inf.ty.TyUnion;
import org.inf.ty.util.TypeComparison;
import org.inf.util.ArrayUtils;

import java.util.Arrays;

/// Checks function-valued use sites after type resolution without inferring or propagating expected types.
@UtilityClass
public class HirFunctionValidationVisitorPass {

  public static void pass(final Hir.Expression expression) {
    expression.visit(new Visitor());
  }

  private static boolean containsFunction(final Ty type) {
    return type instanceof TyFn
      || type instanceof TyUnion union && ArrayUtils.any(union.types(), HirFunctionValidationVisitorPass::containsFunction);
  }

  private static void check(final Hir.Expression expression, final Ty expected, final String context) {
    final var actual = expression.ty() == Ty.DEADEND && !containsFunction(expression.valueTy())
      ? Ty.DEADEND : expression.valueTy();
    check(actual, expected, context);
  }

  private static void check(final Ty actual, final Ty expected, final String context) {
    if (actual == Ty.DEADEND || !(containsFunction(actual) || containsFunction(expected))) {
      return;
    }
    if (TypeComparison.sameValueType(actual, expected)) {
      return;
    }
    if (expected instanceof TyUnion union && !(actual instanceof TyUnion)
      && ArrayUtils.any(union.types(), member -> TypeComparison.sameValueType(actual, member))) {
      return;
    }
    throw new InvalidTypeConversionException(
      "Function type does not match expected function type in %s".formatted(context), actual, expected
    );
  }

  private static final class Visitor implements HirVisitor {

    private Ty returnType;

    @Override
    public void visitAssignment(final Hir.Assignment expression) {
      HirVisitor.super.visitAssignment(expression);
      if (!(expression.lhs() instanceof Hir.Lexeme)) {
        check(expression.rhs(), expression.lhs().valueTy(), "assignment");
      }
    }

    @Override
    public void visitNewByBlock(final Hir.NewByBlock expression) {
      HirVisitor.super.visitNewByBlock(expression);
      if (expression.valueTy() instanceof TyStruct struct) {
        for (final var assignment : expression.fields()) {
          if (assignment.lhs() instanceof Hir.Lexeme name) {
            final var field = Arrays.stream(struct.fields()).filter(candidate -> name.name().equals(candidate.name()))
              .findFirst();
            if (field.isPresent()) {
              check(assignment.rhs(), field.get().ty(), "field %s".formatted(name.name()));
            } else if (containsFunction(assignment.rhs().valueTy())) {
              throw new IllegalArgumentException("Unknown struct field: %s".formatted(name.name()));
            }
          }
        }
      }
    }

    @Override
    public void visitFunction(final Hir.Function expression) {
      final var outerReturn = returnType;
      try {
        returnType = expression.signature().returnType().ty();
        HirVisitor.super.visitFunction(expression);
        check(expression.body(), returnType, "implicit return");
      } finally {
        returnType = outerReturn;
      }
    }

    @Override
    public void visitReturn(final Hir.Return expression) {
      HirVisitor.super.visitReturn(expression);
      if (returnType != null) {
        check(expression.expression(), returnType, "return");
      }
    }

    @Override
    public void visitCall(final Hir.Call expression) {
      HirVisitor.super.visitCall(expression);
      if (!(expression.target().valueTy() instanceof TyFn function)
        || ArrayUtils.none(function.parameters(), parameter -> containsFunction(parameter.ty()))) {
        return;
      }
      final var arguments = expression.arguments();
      final var parameters = function.parameters();
      final var binding = new HirArgumentBinding(
        ArrayUtils.mapToStrings(parameters, TyParam::name), function.vararg(), HirCallArguments.count(arguments)
      );
      for (final var argument : arguments) {
        final var indices = HirCallArguments.bind(argument, binding);
        for (var i = 0; i < indices.length; i++) {
          final var index = indices[i];
          if (index >= 0 && index < parameters.length) {
            if (argument.value() instanceof Hir.Spread spread) {
              check(HirSpreadShape.fields(spread)[i].ty(), parameters[index].ty(), "argument %s".formatted(index));
            } else {
              check(argument.value(), parameters[index].ty(), "argument %s".formatted(index));
            }
          }
        }
      }
      if (expression.ty() != Ty.DEADEND || ArrayUtils.none(arguments, argument -> argument.value() instanceof Hir.Spread)) {
        binding.requireComplete();
      }
    }
  }
}
