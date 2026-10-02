package org.inf.hir.passes;

import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirArgumentBinding;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyParam;
import org.inf.ty.TyStruct;
import org.inf.ty.TyUnion;
import org.inf.ty.util.TupleTypes;
import org.inf.ty.util.TypeComparison;

import java.util.Arrays;

/// Check tuple boundaries after inference, including expressions that cannot execute.
public final class HirTupleValidationVisitorPass {

  private HirTupleValidationVisitorPass() {
  }

  public static void pass(Hir.Expression expression) {
    expression.visit(new Visitor());
  }

  private static void check(Ty actual, Ty expected, String context) {
    if (actual == Ty.DEADEND || !(TupleTypes.containsTuple(actual) || TupleTypes.containsTuple(expected))) {
      return;
    }
    if (TypeComparison.sameValueType(actual, expected)) {
      return;
    }
    if (expected instanceof TyUnion union && !(actual instanceof TyUnion)
      && Arrays.stream(union.types()).anyMatch(type -> TypeComparison.sameValueType(actual, type))) {
      return;
    }
    throw new InvalidTypeConversionException("Incompatible tuple shape or slot type in " + context, actual, expected);
  }

  private static final class Visitor implements HirVisitor {

    private Ty returnType;

    @Override
    public void visitAssignment(Hir.Assignment expression) {
      HirVisitor.super.visitAssignment(expression);
      // Constructor field names acquire their types from the aggregate, not identifier resolution.
      if (!(expression.lhs() instanceof Hir.Lexeme)) {
        check(expression.rhs().ty(), expression.lhs().valueTy(), "assignment");
      }
    }

    @Override
    public void visitNewByBlock(Hir.NewByBlock expression) {
      HirVisitor.super.visitNewByBlock(expression);
      if (expression.valueTy() instanceof TyStruct struct) {
        for (final var assignment : expression.fields()) {
          if (assignment.lhs() instanceof Hir.Lexeme name) {
            final var field = Arrays.stream(struct.fields())
              .filter(candidate -> name.name().equals(candidate.name()))
              .findFirst();
            if (field.isPresent()) {
              check(assignment.rhs().ty(), field.get().ty(), "field " + name.name());
            } else if (TupleTypes.containsTuple(assignment.rhs().ty())) {
              throw new IllegalArgumentException("Unknown struct field: " + name.name());
            }
          }
        }
      }
    }

    @Override
    public void visitCall(Hir.Call expression) {
      HirVisitor.super.visitCall(expression);
      if (!(expression.target().valueTy() instanceof TyFn function)) {
        return;
      }
      if (!TupleTypes.containsTuple(function)
        && Arrays.stream(expression.arguments()).noneMatch(argument -> TupleTypes.containsTuple(argument.ty()))) {
        return;
      }
      final var parameters = function.parameters();
      final var binding = new HirArgumentBinding(
        Arrays.stream(parameters).map(TyParam::name).toArray(String[]::new),
        function.vararg(), expression.arguments().length
      );
      for (final var argument : expression.arguments()) {
        final var index = binding.bind(argument.label() == null ? null : argument.label().name());
        if (index < parameters.length) {
          check(argument.ty(), parameters[index].ty(), "argument " + index);
        }
      }
      binding.requireComplete();
    }

    @Override
    public void visitFunction(Hir.Function expression) {
      final var outerReturnType = returnType;
      try {
        returnType = expression.signature().returnType().ty();
        HirVisitor.super.visitFunction(expression);
        check(expression.body().ty(), returnType, "implicit return");
      } finally {
        returnType = outerReturnType;
      }
    }

    @Override
    public void visitReturn(Hir.Return expression) {
      HirVisitor.super.visitReturn(expression);
      if (returnType != null) {
        check(expression.expression().ty(), returnType, "return");
      }
    }
  }
}
