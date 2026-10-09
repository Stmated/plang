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
import org.inf.ty.TyValueArray;
import org.inf.ty.util.TypeComparison;
import org.inf.ty.util.Tys;
import org.inf.util.ArrayUtils;

import java.util.Objects;

/// Checks array element types and exact declared lengths at value boundaries.
@UtilityClass
public class HirArrayValidationVisitorPass {

  public static void pass(final Hir.Expression expression) {
    expression.visit(new Visitor());
  }

  private static boolean containsArray(final Ty type) {
    return type instanceof TyValueArray
      || type instanceof TyUnion union && ArrayUtils.any(union.types(), HirArrayValidationVisitorPass::containsArray);
  }

  private static boolean compatible(final Ty actual, final Ty expected) {
    if (actual == Ty.DEADEND || TypeComparison.sameValueType(actual, expected)) {
      return true;
    }
    if (actual instanceof TyUnion union) {
      return ArrayUtils.none(union.types(), member -> !compatible(member, expected));
    }
    if (expected instanceof TyUnion union) {
      return ArrayUtils.any(union.types(), member -> compatible(actual, member));
    }
    return actual instanceof TyValueArray source && expected instanceof TyValueArray destination
      && (destination.size() == null || Objects.equals(source.size(), destination.size()))
      && compatible(source.elementType(), destination.elementType());
  }

  private static void check(final Ty actual, final Ty expected, final String context) {
    if (!(containsArray(actual) || containsArray(expected)) || compatible(actual, expected)) {
      return;
    }
    throw new InvalidTypeConversionException("Array type does not match expected array type in " + context, actual, expected);
  }

  private static final class Visitor implements HirVisitor {

    private Ty returnType;

    @Override
    public void visitAssignment(final Hir.Assignment expression) {
      HirVisitor.super.visitAssignment(expression);
      check(expression.rhs().ty(), Tys.getBindingTy(expression.lhs()), "assignment");
    }

    @Override
    public void visitNewByBlockField(final Hir.NewByBlock construction, final Hir.Assignment expression) {
      HirVisitor.super.visitAssignment(expression);
      final var field = Tys.getInitializerField(construction, expression);
      if (field != null) {
        check(expression.rhs().ty(), field.ty(), "field %s".formatted(expression.lhs()));
      }
    }

    @Override
    public void visitCall(final Hir.Call expression) {
      HirVisitor.super.visitCall(expression);
      final var function = Tys.getCallableSignature(expression.target());
      // Variadic receiver packing is not an ordinary array argument boundary.
      if (function == null || function.vararg()) {
        return;
      }
      final var arguments = expression.arguments();
      if (ArrayUtils.none(function.parameters(), parameter -> containsArray(parameter.ty()))
        && ArrayUtils.none(arguments, argument -> argument.value() instanceof Hir.Spread || containsArray(argument.ty()))) {
        return;
      }
      final var parameters = function.parameters();
      final var binding = new HirArgumentBinding(
        ArrayUtils.mapToStrings(parameters, TyParam::name), function.vararg(), HirCallArguments.count(arguments)
      );
      for (final var argument : arguments) {
        final var indices = HirCallArguments.bind(argument, binding);
        final var fields = argument.value() instanceof Hir.Spread spread ? HirSpreadShape.fields(spread) : null;
        for (var i = 0; i < indices.length; i++) {
          final var index = indices[i];
          if (index >= 0 && index < parameters.length) {
            check(fields == null ? argument.ty() : fields[i].ty(), parameters[index].ty(), "argument " + index);
          }
        }
      }
      if (expression.ty() != Ty.DEADEND || ArrayUtils.none(arguments, argument -> argument.value() instanceof Hir.Spread)) {
        binding.requireComplete();
      }
    }

    @Override
    public void visitFunction(final Hir.Function expression) {
      final var outerReturn = returnType;
      try {
        returnType = expression.signature().ty().returnTy();
        HirVisitor.super.visitFunction(expression);
        check(HirBodyResultTyping.resolve(expression.body()), returnType, "function result");
      } finally {
        returnType = outerReturn;
      }
    }

    @Override
    public void visitReturn(final Hir.Return expression) {
      HirVisitor.super.visitReturn(expression);
      if (returnType != null) {
        check(expression.expression().ty(), returnType, "return");
      }
    }
  }
}
