package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirArgumentBinding;
import org.inf.hir.HirCallArguments;
import org.inf.hir.HirSpreadShape;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyParam;
import org.inf.ty.TyUnion;
import org.inf.ty.util.TypeComparison;
import org.inf.ty.util.Tys;
import org.inf.util.ArrayUtils;

/// Checks function-valued use sites after type resolution without inferring or propagating expected types.
@UtilityClass
public class HirFunctionValidationVisitorPass {

  public static void pass(final Hir.Expression expression) {
    expression.visit(new Visitor());
  }

  private static void check(final Hir.Expression expression, final Ty expected, final String context) {
    final var callable = Tys.getCallableValueTy(expression);
    check(callable == null ? expression.ty() : callable, expected, context);
  }

  private static void check(final Ty actual, final Ty expected, final String context) {
    if (actual == Ty.DEADEND || !(Tys.containsFunction(actual) || Tys.containsFunction(expected))) {
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
      check(expression.rhs(), Tys.getBindingTy(expression.lhs()), "assignment");
    }

    @Override
    public void visitNewByBlockField(final Hir.NewByBlock construction, final Hir.Assignment expression) {
      HirVisitor.super.visitAssignment(expression);
      final var field = Tys.getInitializerField(construction, expression);
      if (field != null) {
        check(expression.rhs(), field.ty(), "field %s".formatted(expression.lhs()));
      }
    }

    @Override
    public void visitFunction(final Hir.Function expression) {
      final var outerReturn = returnType;
      try {
        returnType = expression.signature().ty().returnTy();
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
      final var function = Tys.getCallableSignature(expression.target());
      if (function == null || ArrayUtils.none(function.parameters(), parameter -> Tys.containsFunction(parameter.ty()))) {
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
