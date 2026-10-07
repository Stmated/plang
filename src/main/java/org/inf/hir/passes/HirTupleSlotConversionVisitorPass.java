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
import org.inf.ty.util.TypeComparison;

/// Inserts slot conversions into fresh tuples with resolved contextual types.
@UtilityClass
public class HirTupleSlotConversionVisitorPass {

  public static void pass(final Hir.Expression expression) {
    expression.visit(new Visitor());
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
      final var actual = expression.value().ty();
      requireSlotConversion(actual, expected);
      if (actual != Ty.DEADEND && !TypeComparison.sameValueType(actual, expected)) {
        expression.value(new Hir.Convert(expression.value(), expected));
      }
    }
  }

  private static void requireSlotConversion(final Ty actual, final Ty expected) {
    if (actual == Ty.DEADEND || TypeComparison.sameValueType(actual, expected)) {
      return;
    }
    if (actual instanceof TyValueNumberInteger source && expected instanceof TyValueNumberInteger target
      && source.flags().equals(target.flags())) {
      final var sourceBits = source.width().value();
      final var targetBits = target.width().value();
      // Signed sources need signed destinations; unsigned sources need an extra sign bit when becoming signed.
      if (source.signed() ? target.signed() && targetBits >= sourceBits
        : targetBits >= sourceBits + (target.signed() ? 1 : 0)) {
        return;
      }
    }
    throw new InvalidTypeConversionException("Incompatible tuple shape or slot conversion", actual, expected);
  }
}
