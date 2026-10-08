package org.inf.hir.passes;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.inf.exceptions.InvalidTypeConversionException;
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
import org.inf.ty.TyValueNumberInteger;
import org.inf.ty.util.Tys;
import org.inf.util.ArrayUtils;
import org.inf.util.IntegerLiterals;

import java.math.BigInteger;

/// Gives unsuffixed integer literals a resolved expected integer type instead of their default type.
/// For example, `val n: uint8 = 1` and `val t: (uint8,) = (1,)` type `1` directly as `uint8`;
/// this permits fitting constants without allowing narrowing conversions of arbitrary integer values.
@UtilityClass
public class HirIntegerLiteralTypingVisitorPass {

  public static void pass(final Hir.Expression expression) {
    expression.visit(new Visitor());
  }

  private static void contextualize(final Hir.Expression expression, final Ty expected) {
    for (final var result : FindResultExpressionsVisitor.find(expression)) {
      result.visit(new ContextVisitor(expected));
    }
  }

  private static void contextualizeLiteral(final Hir.Literal literal, final Ty expected) {
    if (!(expected instanceof final TyValueNumberInteger target)
      || !(literal.ty() instanceof final TyValueNumberInteger source)
      || source.width().explicit()
      || source.width().value() != Ty.INTEGER.width().value()) {
      return;
    }

    final var value = IntegerLiterals.parse(literal.content(), source.radix());
    final var magnitudeBits = target.width().value() - (target.signed() ? 1 : 0);
    final var limit = BigInteger.ONE.shiftLeft(magnitudeBits);
    final var minimum = target.signed() ? limit.negate() : BigInteger.ZERO;
    if (value.compareTo(minimum) < 0 || value.compareTo(limit) >= 0) {
      throw new InvalidTypeConversionException("Integer literal does not fit expected type", source, target);
    }
    literal.content(value.toString(target.radix()));
    literal.ty(target);
  }

  private static final class Visitor implements HirVisitor {

    @Override
    public void visitAssignment(final Hir.Assignment expression) {
      if (!(expression.lhs() instanceof Hir.Lexeme)) {
        contextualize(expression.rhs(), Tys.getAssignmentContextTy(expression.lhs()));
      }
      HirVisitor.super.visitAssignment(expression);
    }

    @Override
    public void visitCompoundAssignment(final Hir.CompoundAssignment expression) {
      contextualize(expression.rhs(), Tys.getBindingTy(expression.target()));
      HirVisitor.super.visitCompoundAssignment(expression);
    }

    @Override
    public void visitFunction(final Hir.Function expression) {
      contextualizeReturns(expression, Tys.getFunctionReturnContextTy(expression.signature()));
      HirVisitor.super.visitFunction(expression);
    }

    @Override
    public void visitArray(final Hir.Array expression) {
      contextualizeElements(expression, expression.elementType().ty());
      HirVisitor.super.visitArray(expression);
    }

    @Override
    public void visitTuple(final Hir.Tuple expression) {
      final var destination = expression.contextualType();
      if (destination != null) {
        expression.visit(new EntryVisitor(destination, HirTupleMatching.match(expression, destination)));
      }
      HirVisitor.super.visitTuple(expression);
    }

    @Override
    public void visitNewByBlock(final Hir.NewByBlock expression) {
      final var struct = Tys.getConstructionTargetTy(expression.target());
      if (struct != null) {
        for (final var assignment : expression.fields()) {
          if (assignment.lhs() instanceof final Hir.Lexeme name) {
            contextualize(assignment.rhs(), Tys.getStructFieldTy(struct, name.name()));
          }
        }
      }
      HirVisitor.super.visitNewByBlock(expression);
    }

    @Override
    public void visitCall(final Hir.Call expression) {
      final var function = Tys.getCallableSignature(expression.target());
      if (function != null) {
        final var parameters = function.parameters();
        final var binding = new HirArgumentBinding(
          ArrayUtils.mapToStrings(parameters, TyParam::name), function.vararg(), HirCallArguments.count(expression.arguments())
        );
        for (final var argument : expression.arguments()) {
          final var indices = HirCallArguments.bind(argument, binding);
          if (argument.value() instanceof final Hir.Spread spread) {
            final var fields = HirSpreadShape.fields(spread);
            final var expected = new TyField[fields.length];
            for (var i = 0; i < fields.length; i++) {
              final var index = indices[i];
              final var type = index >= 0 && index < parameters.length ? parameters[index].ty() : Ty.INFER;
              expected[i] = new TyField(fields[i].name(), type);
            }
            contextualize(spread.value(), new TyStruct(expected, true));
          } else if (indices[0] >= 0 && indices[0] < parameters.length) {
            contextualize(argument.value(), parameters[indices[0]].ty());
          }
        }
      }
      HirVisitor.super.visitCall(expression);
    }
  }

  private static void contextualizeReturns(final Hir.Function expression, final Ty expected) {
    for (final var result : FindResultExpressionsVisitor.findReturns(expression.body())) {
      contextualize(result, expected);
    }
  }

  private static void contextualizeElements(final Hir.Array expression, final Ty expected) {
    for (final var element : expression.elements()) {
      contextualize(element, expected);
    }
  }

  private record ContextVisitor(Ty expected) implements HirVisitor {

    @Override
    public void visitChild(final Hir.Expression expression) {
    }

    @Override
    public void visitLiteral(final Hir.Literal expression) {
      contextualizeLiteral(expression, expected);
    }

    @Override
    public void visitTuple(final Hir.Tuple expression) {
      if (expected instanceof final TyStruct tuple && tuple.tuple()) {
        expression.visit(new EntryVisitor(tuple, HirTupleMatching.match(expression, tuple)));
      }
    }

    @Override
    public void visitArray(final Hir.Array expression) {
      if (expected instanceof final TyValueArray array) {
        final var declared = expression.elementType().ty();
        contextualizeElements(expression, Tys.isInferred(declared) ? array.elementType() : declared);
      }
    }

    @Override
    public void visitFunction(final Hir.Function expression) {
      if (expected instanceof final TyFn function) {
        contextualizeReturns(expression, Tys.getFunctionReturnContextTy(expression.signature(), function.returnTy()));
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
      if (slots == null) {
        return;
      }
      final var expected = destination.fields()[slots[index++]].ty();
      contextualize(expression.value(), expected);
    }
  }
}
