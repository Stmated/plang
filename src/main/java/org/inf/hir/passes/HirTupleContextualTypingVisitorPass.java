package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.hir.Hir;
import org.inf.hir.HirArgumentBinding;
import org.inf.hir.HirTupleMatching;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyParam;
import org.inf.ty.TyStruct;
import org.inf.ty.TyValueNumberInteger;
import org.inf.ty.util.TypeComparison;
import org.inf.util.ArrayUtils;
import org.inf.util.IntegerLiterals;

import java.math.BigInteger;
import java.util.Arrays;

/// Rewrites fresh tuple elements before enclosing expression types are resolved.
@UtilityClass
public class HirTupleContextualTypingVisitorPass {

  public static void pass(Hir.Expression expression) {
    new Visitor().visitChild(expression);
  }

  private static final class Visitor implements HirVisitor {

    private Ty expectedType;
    private Ty returnType;
    private TyStruct tupleType;
    private int[] tupleSlots;
    private int tupleIndex;

    @Override
    public void visitChild(Hir.Expression expression) {
      visitExpected(expression, null);
    }

    private void visitExpected(Hir.Expression expression, Ty expected) {
      final var outerExpected = expectedType;
      try {
        expectedType = expected;
        expression.visit(this);
        HirTyCommonVisitorPass.resolveNode(expression);
      } finally {
        expectedType = outerExpected;
      }
    }

    private static TyStruct tupleType(Ty type) {
      return type instanceof TyStruct struct && struct.tuple() ? struct : null;
    }

    @Override
    public void visitAssignment(Hir.Assignment expression) {
      visitAssignmentLhs(expression.lhs());
      final var expected = expression.lhs() instanceof Hir.Dec || expression.lhs() instanceof Hir.Identifier
        ? tupleType(expression.lhs().valueTy()) : null;
      visitExpected(expression.rhs(), expected);
    }

    @Override
    public void visitFunction(Hir.Function expression) {
      visitChild(expression.signature());
      final var outerReturn = returnType;
      try {
        returnType = tupleType(expression.signature().returnType().ty());
        visitExpected(expression.body(), returnType);
      } finally {
        returnType = outerReturn;
      }
    }

    @Override
    public void visitReturn(Hir.Return expression) {
      visitExpected(expression.expression(), returnType);
    }

    @Override
    public void visitCall(Hir.Call expression) {
      visitChild(expression.target());
      final var arguments = expression.arguments();
      if (!(expression.target().valueTy() instanceof TyFn function)
        || Arrays.stream(function.parameters()).noneMatch(parameter -> tupleType(parameter.ty()) != null)) {
        visitCallArguments(expression.arguments());
        return;
      }
      final var parameters = function.parameters();
      final var binding = new HirArgumentBinding(
        ArrayUtils.mapToStrings(parameters, TyParam::name), function.vararg(), arguments.length
      );
      for (final var argument : arguments) {
        final var index = binding.bind(argument.label() == null ? null : argument.label().name());
        visitExpected(argument.value(), index < parameters.length ? tupleType(parameters[index].ty()) : null);
      }
      binding.requireComplete();
    }

    @Override
    public void visitBlock(Hir.Block expression) {
      visitExpected(expression.children(), expectedType);
    }

    @Override
    public void visitExpressions(Hir.Expressions expression) {
      final var expected = expectedType;
      final var children = expression.children();
      for (var i = 0; i < children.length; i++) {
        visitExpected(children[i], i == children.length - 1 ? expected : null);
      }
    }

    @Override
    public void visitConditional(Hir.Conditional expression) {
      final var expected = expectedType;
      visitChild(expression.predicate());
      visitExpected(expression.pass(), expected);
      if (expression.fail() != null) {
        visitExpected(expression.fail(), expected);
      }
    }

    @Override
    public void visitTuple(Hir.Tuple expression) {
      final var expected = tupleType(expectedType);
      final var outerTuple = tupleType;
      final var outerSlots = tupleSlots;
      final var outerIndex = tupleIndex;
      try {
        final var destination = expected == null ? expression.contextualType() : expected;
        tupleSlots = destination == null ? null : HirTupleMatching.match(expression, destination);
        tupleType = tupleSlots == null ? null : destination;
        expression.contextualType(tupleType);
        tupleIndex = 0;
        HirVisitor.super.visitTuple(expression);
      } finally {
        tupleType = outerTuple;
        tupleSlots = outerSlots;
        tupleIndex = outerIndex;
      }
    }

    @Override
    public void visitTupleEntry(Hir.TupleEntry expression) {
      final var expected = tupleType == null ? null : tupleType.fields()[tupleSlots[tupleIndex++]].ty();
      visitExpected(expression.value(), expected);
      if (expected != null) {
        final var actual = expression.value().ty();
        requireSlotConversion(actual, expected);
        if (actual != Ty.DEADEND && !TypeComparison.sameValueType(actual, expected)) {
          expression.value(new Hir.Convert(expression.value(), expected));
        }
      }
    }

    @Override
    public void visitLiteral(Hir.Literal expression) {
      contextualizeLiteral(expression, expectedType);
    }
  }

  private static void contextualizeLiteral(Hir.Literal literal, Ty expected) {
    // `L` suffixes are stripped by the lexer but retain their nondefault literal width.
    if (!(expected instanceof TyValueNumberInteger target)
      || !(literal.ty() instanceof TyValueNumberInteger source) || source.width().explicit()
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

  private static void requireSlotConversion(Ty actual, Ty expected) {
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
