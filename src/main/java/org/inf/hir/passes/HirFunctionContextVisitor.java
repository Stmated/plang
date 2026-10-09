package org.inf.hir.passes;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirArgumentBinding;
import org.inf.hir.HirCallArguments;
import org.inf.hir.HirSpreadShape;
import org.inf.hir.HirTupleMatching;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyParam;
import org.inf.ty.TyStruct;
import org.inf.ty.TyValueArray;
import org.inf.ty.util.Tys;
import org.inf.util.ArrayUtils;

import java.util.function.BiConsumer;

/// Selects lambda signatures and their resolved use-site contexts without resolving types.
@UtilityClass
final class HirFunctionContextVisitor {

  static void visit(final Hir.Expression expression, final BiConsumer<Hir.FunctionSignature, TyFn> action) {
    expression.visit(new Visitor(action));
  }

  @RequiredArgsConstructor
  private static final class Visitor implements HirVisitor {

    private final BiConsumer<Hir.FunctionSignature, TyFn> action;

    private void visitContext(final Hir.Expression expression, final Ty type) {
      for (final var result : FindResultExpressionsVisitor.find(expression)) {
        result.visit(new ContextVisitor(type));
      }
    }

    private void visitReturns(final Hir.Function expression, final Ty type) {
      for (final var result : FindResultExpressionsVisitor.findReturns(expression.body())) {
        visitContext(result, type);
      }
    }

    @RequiredArgsConstructor
    private final class ContextVisitor implements HirVisitor {

      private final Ty expected;

      @Override
      public void visitChild(final Hir.Expression expression) {
      }

      @Override
      public void visitFunction(final Hir.Function expression) {
        if (expected instanceof final TyFn function) {
          action.accept(expression.signature(), function);
          visitReturns(expression, Tys.getFunctionReturnContextTy(expression.signature(), function.returnTy()));
        }
      }

      @Override
      public void visitTuple(final Hir.Tuple expression) {
        if (expected instanceof final TyStruct tuple && tuple.tuple()) {
          final var slots = HirTupleMatching.match(expression, tuple);
          if (slots != null) {
            expression.visit(new EntryVisitor(tuple, slots));
          }
        }
      }

      @Override
      public void visitArray(final Hir.Array expression) {
        if (expected instanceof final TyValueArray array) {
          for (final var element : expression.elements()) {
            visitContext(element, array.elementType());
          }
        }
      }
    }

    @RequiredArgsConstructor
    private final class EntryVisitor implements HirVisitor {

      private final TyStruct destination;
      private final int[] slots;
      private int index;

      @Override
      public void visitTupleEntry(final Hir.TupleEntry expression) {
        visitContext(expression.value(), destination.fields()[slots[index++]].ty());
      }
    }

    @Override
    public void visitAssignment(final Hir.Assignment expression) {
      visitContext(expression.rhs(), Tys.getAssignmentContextTy(expression.lhs()));
      HirVisitor.super.visitAssignment(expression);
    }

    @Override
    public void visitNewByBlockField(final Hir.NewByBlock construction, final Hir.Assignment expression) {
      final var field = Tys.getInitializerField(construction, expression);
      if (field != null) {
        visitContext(expression.rhs(), field.ty());
      }
      HirVisitor.super.visitAssignment(expression);
    }

    @Override
    public void visitFunction(final Hir.Function expression) {
      visitReturns(expression, Tys.getFunctionReturnContextTy(expression.signature()));
      HirVisitor.super.visitFunction(expression);
    }

    @Override
    public void visitCall(final Hir.Call expression) {
      final var function = Tys.getCallableSignature(expression.target());
      if (function != null && ArrayUtils.any(function.parameters(), parameter -> parameter.ty() instanceof TyFn)) {
        final var arguments = expression.arguments();
        if (ArrayUtils.none(arguments, argument -> argument.value() instanceof Hir.Spread spread
          && HirSpreadShape.availableFields(spread) == null)) {
          final var parameters = function.parameters();
          final var binding = new HirArgumentBinding(
            ArrayUtils.mapToStrings(parameters, TyParam::name), function.vararg(), HirCallArguments.count(arguments)
          );
          for (final var argument : arguments) {
            final var indices = HirCallArguments.bind(argument, binding);
            if (!(argument.value() instanceof Hir.Spread) && indices[0] >= 0 && indices[0] < parameters.length) {
              visitContext(argument.value(), parameters[indices[0]].ty());
            }
          }
        }
      }
      HirVisitor.super.visitCall(expression);
    }
  }
}
