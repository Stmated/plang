package org.inf.hir.passes;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirArgumentBinding;
import org.inf.hir.HirCallArguments;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyField;
import org.inf.ty.TyFn;
import org.inf.ty.TyParam;
import org.inf.ty.TyStruct;
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
      if (type instanceof final TyFn expected) {
        for (final var result : FindResultExpressionsVisitor.find(expression)) {
          if (result instanceof final Hir.Function lambda) {
            action.accept(lambda.signature(), expected);
            final var declaredReturn = lambda.signature().returnType().ty();
            visitReturns(lambda, Tys.isInferred(declaredReturn) ? expected.returnTy() : declaredReturn);
          }
        }
      }
    }

    private void visitReturns(final Hir.Function expression, final Ty type) {
      if (type instanceof TyFn) {
        FindResultExpressionsVisitor.findReturns(expression.body()).forEach(result -> visitContext(result, type));
      }
    }

    @Override
    public void visitAssignment(final Hir.Assignment expression) {
      if (!(expression.lhs() instanceof Hir.Lexeme)) {
        visitContext(expression.rhs(), expression.lhs().valueTy());
      }
      HirVisitor.super.visitAssignment(expression);
    }

    @Override
    public void visitNewByBlock(final Hir.NewByBlock expression) {
      if (expression.target().valueTy() instanceof final TyStruct struct) {
        for (final var assignment : expression.fields()) {
          if (assignment.lhs() instanceof final Hir.Lexeme name) {
            for (final TyField field : struct.fields()) {
              if (name.name().equals(field.name())) {
                visitContext(assignment.rhs(), field.ty());
                break;
              }
            }
          }
        }
      }
      HirVisitor.super.visitNewByBlock(expression);
    }

    @Override
    public void visitFunction(final Hir.Function expression) {
      visitReturns(expression, expression.signature().returnType().ty());
      HirVisitor.super.visitFunction(expression);
    }

    @Override
    public void visitCall(final Hir.Call expression) {
      if (expression.target().valueTy() instanceof final TyFn function
        && ArrayUtils.any(function.parameters(), parameter -> parameter.ty() instanceof TyFn)) {
        final var arguments = expression.arguments();
        if (ArrayUtils.none(arguments, argument -> argument.value() instanceof Hir.Spread spread
          && !(spread.value().valueTy() instanceof TyStruct))) {
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
