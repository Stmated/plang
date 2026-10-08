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
import org.inf.ty.TyField;
import org.inf.ty.TyFn;
import org.inf.ty.TyParam;
import org.inf.ty.TyStruct;
import org.inf.ty.TyValueArray;
import org.inf.ty.util.Tys;
import org.inf.util.ArrayUtils;

/// Links fresh tuple constructions to resolved destination layouts.
@UtilityClass
public class HirTupleContextVisitorPass {

  public static void pass(final Hir.Expression expression) {
    expression.visit(new Visitor());
  }

  private static void link(final Hir.Expression expression, final Ty type) {
    for (final var result : FindResultExpressionsVisitor.find(expression)) {
      result.visit(new ContextVisitor(type));
    }
  }

  private record ContextVisitor(Ty expected) implements HirVisitor {

    @Override
    public void visitChild(final Hir.Expression expression) {
    }

    @Override
    public void visitTuple(final Hir.Tuple expression) {
      if (expected instanceof final TyStruct tuple && tuple.tuple()) {
        final var slots = HirTupleMatching.match(expression, tuple);
        expression.contextualType(slots == null ? null : tuple);
        if (slots != null) {
          expression.visit(new EntryVisitor(tuple, slots));
        }
      }
    }

    @Override
    public void visitArray(final Hir.Array expression) {
      if (expected instanceof final TyValueArray array) {
        for (final var element : expression.elements()) {
          link(element, array.elementType());
        }
      }
    }

    @Override
    public void visitFunction(final Hir.Function expression) {
      if (expected instanceof final TyFn function) {
        final var type = Tys.getFunctionReturnContextTy(expression.signature(), function.returnTy());
        for (Hir.Expression result : FindResultExpressionsVisitor.findReturns(expression.body())) {
          link(result, type);
        }
      }
    }
  }

  private static void linkArguments(final Hir.Call expression) {
    final var function = Tys.getCallableSignature(expression.target());
    if (function == null
      || ArrayUtils.none(function.parameters(), parameter -> parameter.ty() instanceof TyStruct tuple && tuple.tuple())) {
      return;
    }
    final var parameters = function.parameters();
    final var arguments = expression.arguments();
    final var binding = new HirArgumentBinding(
      ArrayUtils.mapToStrings(parameters, TyParam::name), function.vararg(), HirCallArguments.count(arguments)
    );
    for (final var argument : arguments) {
      final var indices = HirCallArguments.bind(argument, binding);
      if (argument.value() instanceof final Hir.Spread spread) {
        final var fields = HirSpreadShape.fields(spread);
        final var expected = new TyField[fields.length];
        var contextual = false;
        for (var i = 0; i < fields.length; i++) {
          final var index = indices[i];
          final var type = index >= 0 && index < parameters.length ? parameters[index].ty() : fields[i].ty();
          final var isTuple = type instanceof TyStruct tuple && tuple.tuple();
          expected[i] = new TyField(fields[i].name(), isTuple ? type : fields[i].ty());
          contextual |= isTuple;
        }
        if (contextual) {
          link(spread.value(), new TyStruct(expected, true));
        }
      } else if (indices[0] >= 0 && indices[0] < parameters.length) {
        link(argument.value(), parameters[indices[0]].ty());
      }
    }
  }

  @RequiredArgsConstructor
  private static final class EntryVisitor implements HirVisitor {

    private final TyStruct destination;
    private final int[] slots;
    private int index;

    @Override
    public void visitTupleEntry(final Hir.TupleEntry expression) {
      link(expression.value(), destination.fields()[slots[index++]].ty());
    }
  }

  private static final class Visitor implements HirVisitor {

    @Override
    public void visitAssignment(final Hir.Assignment expression) {
      if (expression.lhs() instanceof Hir.Identifier || expression.lhs() instanceof Hir.Dec) {
        link(expression.rhs(), Tys.getAssignmentContextTy(expression.lhs()));
      }
      HirVisitor.super.visitAssignment(expression);
    }

    @Override
    public void visitFunction(final Hir.Function expression) {
      final var type = Tys.getFunctionReturnContextTy(expression.signature());
      for (final var result : FindResultExpressionsVisitor.findReturns(expression.body())) {
        link(result, type);
      }
      HirVisitor.super.visitFunction(expression);
    }

    @Override
    public void visitCall(final Hir.Call expression) {
      linkArguments(expression);
      HirVisitor.super.visitCall(expression);
    }
  }
}
