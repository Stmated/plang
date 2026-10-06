package org.inf.hir.passes;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirTupleMatching;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyStruct;
import org.inf.ty.TyValueNumberInteger;
import org.inf.util.IntegerLiterals;

import java.math.BigInteger;

/// Adapts unsuffixed integer literals to their linked tuple slots.
@UtilityClass
public class HirTupleLiteralTypingVisitorPass {

  public static void pass(final Hir.Expression expression) {
    expression.visit(new Visitor());
  }

  private static void contextualize(final Hir.Literal literal, final Ty expected) {
    // `L` suffixes are stripped by the lexer but retain their nondefault literal width.
    if (!(expected instanceof final TyValueNumberInteger target)
      || !(literal.ty() instanceof final TyValueNumberInteger source) || source.width().explicit()
      || source.width().value() != Ty.INTEGER.width().value()) {
      return;
    }
    final var value = IntegerLiterals.parse(literal.content(), source.radix());
    final var magnitudeBits = target.width().value() - (target.signed() ? 1 : 0);
    final var limit = BigInteger.ONE.shiftLeft(magnitudeBits);
    final var minimum = target.signed() ? limit.negate() : BigInteger.ZERO;
    if (value.compareTo(minimum) < 0 || value.compareTo(limit) >= 0) {
      throw new InvalidTypeConversionException("Integer literal does not fit tuple slot", source, target);
    }
    literal.content(value.toString(target.radix()));
    literal.ty(target);
  }

  private static final class Visitor implements HirVisitor {

    @Override
    public void visitTuple(final Hir.Tuple expression) {
      HirVisitor.super.visitTuple(expression);
      final var destination = expression.contextualType();
      if (destination != null) {
        expression.visit(new EntryVisitor(destination, HirTupleMatching.match(expression, destination)));
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
      for (final var result : FindResultExpressionsVisitor.find(expression.value())) {
        if (result instanceof final Hir.Literal literal) {
          contextualize(literal, expected);
        }
      }
    }
  }
}
