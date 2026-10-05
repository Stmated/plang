package org.inf.hir.passes;

import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirArgumentBinding;
import org.inf.hir.HirCallArguments;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyParam;
import org.inf.ty.TyStruct;
import org.inf.ty.TyUnion;
import org.inf.ty.util.TupleTypes;
import org.inf.ty.util.TypeComparison;

import java.util.Arrays;
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

    private void visitAnnotation(Hir.Expression expression) {
      final var outerAnnotation = inAnnotation;
      try {
        inAnnotation = true;
        visitChild(expression);
      } finally {
        inAnnotation = outerAnnotation;
      }
    }

    @Override
    public void visitDecType(Hir.Expression expression) {
      visitAnnotation(expression);
    }

    @Override
    public void visitParameterType(Hir.Expression expression) {
      visitAnnotation(expression);
    }

    @Override
    public void visitFunctionSignatureReturnType(Hir.Expression expression) {
      visitAnnotation(expression);
    }

    @Override
    public void visitArrayElementType(Hir.Expression expression) {
      visitAnnotation(expression);
    }

    @Override
    public void visitTupleEntry(Hir.TupleEntry expression) {
      if (inAnnotation) {
        final var value = expression.value();
        if (!isTypeExpression(value)) {
          throw new IllegalArgumentException("Tuple annotations require types, not value expressions");
        }
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
            case Hir.TyExpr _, Hir.Tuple _ -> {
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
      // Constructor field names acquire their types from the aggregate, not identifier resolution.
      if (!(expression.lhs() instanceof Hir.Lexeme)) {
        check(expression.rhs().ty(), expression.lhs().valueTy(), "assignment");
      }
    }

    @Override
    public void visitCompoundAssignment(Hir.CompoundAssignment expression) {
      HirVisitor.super.visitCompoundAssignment(expression);
      requireWritable(expression.target());
    }

    private static void requireWritable(Hir.Expression expression) {
      if (expression instanceof Hir.ArrayAccess access
        && access.target().valueTy() instanceof TyStruct tuple && tuple.tuple()) {
        throw new IllegalArgumentException("Tuple element writes are not supported yet");
      }
      if (expression instanceof Hir.Path path && path.elements().length >= 2
        && path.elements()[path.elements().length - 2].valueTy() instanceof TyStruct tuple && tuple.tuple()) {
        throw new IllegalArgumentException("Tuple element writes are not supported yet");
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
      final var arguments = HirCallArguments.entries(expression.arguments());
      if (!TupleTypes.containsTuple(function) && arguments.stream().noneMatch(it -> TupleTypes.containsTuple(it.ty()))) {
        return;
      }
      final var parameters = function.parameters();
      final var binding = new HirArgumentBinding(
        ArrayUtils.mapToStrings(parameters, TyParam::name),
        function.vararg(), arguments.size()
      );
      for (final var argument : arguments) {
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
