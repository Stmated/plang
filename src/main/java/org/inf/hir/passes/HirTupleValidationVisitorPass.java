package org.inf.hir.passes;

import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirArgumentBinding;
import org.inf.hir.HirCallArguments;
import org.inf.hir.HirSpreadShape;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyParam;
import org.inf.ty.TyStruct;
import org.inf.ty.TyUnion;
import org.inf.ty.util.TupleTypes;
import org.inf.ty.util.TypeComparison;
import org.inf.ty.util.Tys;

import org.inf.util.ArrayUtils;

/// Check tuple boundaries after inference, including expressions that cannot execute.
public final class HirTupleValidationVisitorPass {

  private HirTupleValidationVisitorPass() {
  }

  public static void pass(Hir.Expression expression) {
    expression.visit(new Visitor());
  }

  /**
   * TODO: This should likely be a more general check for all return types of functions, just just for tuples.
   */
  private static void check(Ty actual, Ty expected, String context) {
    if (actual == Ty.DEADEND || !(TupleTypes.containsTuple(actual) || TupleTypes.containsTuple(expected))) {
      return;
    }
    if (TypeComparison.sameValueType(actual, expected)) {
      return;
    }
    if (expected instanceof TyUnion union && !(actual instanceof TyUnion) && ArrayUtils.any(union.types(), t -> TypeComparison.sameValueType(actual, t))) {
      return;
    }
    throw new InvalidTypeConversionException("Incompatible tuple shape or slot type in " + context, actual, expected);
  }

  private static final class Visitor implements HirVisitor {

    private Ty returnType;

    /// HIR uses the same tuple node for `(int, bool)` annotations and `(1, true)` values.
    /// Only annotation entries must denote types; their enclosing type position supplies that distinction.
    private boolean inAnnotation;

    private void visitAnnotation(Hir.DynamicTy annotation) {
      final var outerAnnotation = inAnnotation;
      try {
        inAnnotation = true;
        annotation.visit(this);
      } finally {
        inAnnotation = outerAnnotation;
      }
    }

    @Override
    public void visitDecType(Hir.DynamicTy expression) {
      visitAnnotation(expression);
    }

    @Override
    public void visitParameterType(Hir.DynamicTy expression) {
      visitAnnotation(expression);
    }

    @Override
    public void visitFunctionSignatureReturnType(Hir.DynamicTy expression) {
      visitAnnotation(expression);
    }

    @Override
    public void visitArrayElementType(Hir.DynamicTy expression) {
      visitAnnotation(expression);
    }

    @Override
    public void visitTupleEntry(Hir.TupleEntry expression) {
      final var value = expression.value();
      if (inAnnotation) {
        if (!isTypeExpression(value)) {
          throw new IllegalArgumentException("Tuple annotations require types, not value expressions");
        }
        TupleTypes.requireElementType(value.ty(), true);
      } else if (value.ty() != Ty.DEADEND) {
        TupleTypes.requireElementType(value.ty());
      }
      HirVisitor.super.visitTupleEntry(expression);
    }

    private static boolean isTypeExpression(Hir.Expression expression) {
      final var visitor = new HirVisitor() {
        private boolean valid = true;

        @Override
        public void visitChild(Hir.Expression child) {
          switch (child) {
            case Hir.BuiltInTy _ -> {
            }
            case Hir.Tuple _ -> {
            }
            case Hir.Array array -> {
              valid &= array.elements().length == 0;
              visitArrayElementType(array.elementType());
            }
            case Hir.Identifier identifier ->
              valid &= identifier.ty() instanceof TyStruct struct && !struct.tuple();
            default -> valid = false;
          }
        }
      };
      visitor.visitChild(expression);
      return visitor.valid;
    }

    @Override
    public void visitAssignment(Hir.Assignment expression) {
      HirVisitor.super.visitAssignment(expression);
      requireWritable(expression.lhs());
      check(expression.rhs().ty(), Tys.getBindingTy(expression.lhs()), "assignment");
    }

    @Override
    public void visitCompoundAssignment(Hir.CompoundAssignment expression) {
      HirVisitor.super.visitCompoundAssignment(expression);
      requireWritable(expression.target());
    }

    private static void requireWritable(Hir.Expression expression) {
      if (expression instanceof Hir.ArrayAccess access
        && Tys.getIndexingReceiverTy(access.target()) instanceof TyStruct tuple && tuple.tuple()) {
        throw new IllegalArgumentException("Tuple element writes are not supported yet");
      }
      if (expression instanceof Hir.DotAccess access
        && Tys.getMemberReceiverTy(access.target()) instanceof TyStruct tuple && tuple.tuple()) {
        throw new IllegalArgumentException("Tuple element writes are not supported yet");
      }
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
    public void visitCall(Hir.Call expression) {
      HirVisitor.super.visitCall(expression);
      final var function = Tys.getCallableSignature(expression.target());
      if (function == null) {
        return;
      }
      final var arguments = expression.arguments();
      if (!TupleTypes.containsTuple(function)
        && ArrayUtils.none(arguments, it -> it.value() instanceof Hir.Spread || TupleTypes.containsTuple(it.ty()))) {
        return;
      }
      final var parameters = function.parameters();
      final var binding = new HirArgumentBinding(
        ArrayUtils.mapToStrings(parameters, TyParam::name),
        function.vararg(), HirCallArguments.count(arguments)
      );
      for (final var argument : arguments) {
        final var indices = HirCallArguments.bind(argument, binding);
        for (var i = 0; i < indices.length; i++) {
          final var index = indices[i];
          if (index >= 0 && index < parameters.length) {
            final var actual = argument.value() instanceof Hir.Spread spread
              ? HirSpreadShape.fields(spread)[i].ty() : argument.ty();
            check(actual, parameters[index].ty(), "argument " + index);
          }
        }
      }
      if (expression.ty() != Ty.DEADEND || ArrayUtils.none(arguments, argument -> argument.value() instanceof Hir.Spread)) {
        binding.requireComplete();
      }
    }

    @Override
    public void visitFunction(Hir.Function expression) {
      final var outerReturnType = returnType;
      try {
        returnType = expression.signature().ty().returnTy();
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
