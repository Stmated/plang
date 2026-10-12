package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.exceptions.UnexpectedExpressionException;
import org.inf.hir.Hir;
import org.inf.hir.HirJavaUtil;
import org.inf.hir.HirTupleAccess;
import org.inf.hir.HirVisitor;
import org.inf.hir.typing.HirBodyResultTyping;
import org.inf.hir.typing.HirDeclarationTyping;
import org.inf.hir.typing.HirFunctionReturnTyping;
import org.inf.hir.typing.HirTupleTyping;
import org.inf.hir.util.HirExprs;
import org.inf.ty.*;
import org.inf.ty.util.Tys;
import org.inf.ty.util.TupleTypes;
import org.inf.ty.util.TypeComparison;
import org.inf.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Objects;

@Slf4j
@UtilityClass
public class HirTyCommonVisitorPass {

  public static <T extends Hir.Expression> T pass(T expr) {
    expr.visit(new Visitor(false, new HirTypeAnnotationResolver(expr)));
    return expr;
  }

  /// TODO: Delete the return from here! It is temporary while I try to figure out the tuple+union+identifire-resolved types
  public static HirTypeAnnotationResolver resolveAvailableTypes(final Hir.Expression expr) {
    final var annotationResolver = new HirTypeAnnotationResolver(expr);
    expr.visit(new Visitor(true, annotationResolver));
    return annotationResolver;
  }

  private record Visitor(boolean availableOnly, HirTypeAnnotationResolver annotations) implements HirVisitor {

    @Override
    public void visitTuple(Hir.Tuple expr) {
      HirVisitor.super.visitTuple(expr);
      HirTupleTyping.resolve(expr, availableOnly, annotations);
    }

    @Override
    public void visitUnion(final Hir.Union expr) {
      HirVisitor.super.visitUnion(expr);

      final var types = new Ty[expr.elements().length];
      for (var i = 0; i < types.length; i++) {
        types[i] = annotations.resolve(expr.elements()[i]);
      }
      final var unionType = Tys.union(types);
      expr.unionTy(unionType);
    }

    @Override
    public void visitParameter(Hir.Parameter expr) {
      HirVisitor.super.visitParameter(expr);
      expr.typeAnnotation(expr.typeAnnotation().resolve(annotations::resolve));
      final var annotation = expr.typeAnnotation().ty();
      if (!Tys.isInferred(annotation) || expr.resolvedTy() == null) {
        expr.resolvedTy(annotation);
      }
    }

    @Override
    public void visitBinaryOperation(Hir.BinaryOperation expr) {

      // Go deeper first, and resolve any nested binary operations.
      HirVisitor.super.visitBinaryOperation(expr);

      final var lhst = Tys.completionTy(expr.lhs().ty());
      final var rhst = Tys.completionTy(expr.rhs().ty());

      if (Tys.isDeadend(lhst)) {
        expr.ty(Ty.DEADEND);
      } else if (!expr.kind().isShortCircuiting() && Tys.isDeadend(rhst)) {
        expr.ty(Ty.DEADEND);
      } else if (expr.kind().isPredicate()) {
        expr.ty(Ty.BOOLEAN);
      } else {
        expr.ty(Objects.requireNonNullElse(Tys.getCommonDenominator(lhst, rhst).ty(), Ty.INFER));
      }
    }

    @Override
    public void visitArray(final Hir.Array expr) {
      HirVisitor.super.visitArray(expr);

      expr.elementType(expr.elementType().resolve(annotations::resolve));

      var arrayElementTy = expr.elementType().ty();
      for (final var element : expr.elements()) {

        if (arrayElementTy instanceof TyValueNumber vn) {
          if (element instanceof Hir.Literal lit && lit.ty() instanceof TyValueNumber litTy_n
            && !(vn instanceof TyValueNumberInteger && litTy_n instanceof TyValueNumberInteger)) {
            if (!litTy_n.width().explicit() && (litTy_n.width().value() != vn.width().value() || litTy_n.signed() != vn.signed())) {

              // Integer-to-integer typing is handled by HirIntegerLiteralTypingVisitorPass.
              lit.ty(vn);
            }
          }
        }

        final var elementTy = Tys.completionTy(element.ty());
        if (Tys.isDeadend(elementTy)) {
          continue;
        }
        if (Tys.isInferred(arrayElementTy)) {
          arrayElementTy = elementTy;
        } else if (!availableOnly && Tys.asStructural(arrayElementTy, TyUnion.class) == null
          && (TupleTypes.containsTuple(arrayElementTy) || TupleTypes.containsTuple(elementTy))) {
          if (!TypeComparison.same(elementTy, arrayElementTy)) {
            throw new InvalidTypeConversionException("Array tuple elements must have matching shapes and slot types", elementTy, arrayElementTy);
          }
        } else if (!availableOnly && Tys.asStructural(arrayElementTy, TyUnion.class) == null) {

          final var common = Tys.getCommonDenominator(arrayElementTy, elementTy);
          final var diffs = common.diffs();
          if (!Tys.isSizeCompatible(diffs)) {
            throw new InvalidTypeConversionException("Array types must be size-compatible", elementTy, arrayElementTy);
          }
        }
      }

      Integer arrayLength;
      if (expr.length() == null || Tys.isDeadend(expr.length().ty())) {
        arrayLength = null;
      } else {
        final var literalValue = HirJavaUtil.resolveLiteralValue(expr.length());
        arrayLength = (literalValue == null) ? null : ((Number) literalValue).intValue();
      }

      // The element ty can still be INFER -- it will be used for late type decisions.
      final var arrayTy = Tys.intern(new TyValueArray(arrayElementTy, arrayLength));
      final var diverges = HirExprs.isDeadEnd(expr.elements())
        || (expr.length() != null && Tys.isDeadend(expr.length().ty()));
      expr.ty(Tys.withCompletion(diverges ? Ty.DEADEND : arrayTy, arrayTy));
    }

    @Override
    public void visitDec(Hir.Dec expr) {
      HirVisitor.super.visitDec(expr);
      expr.typeAnnotation(expr.typeAnnotation().resolve(annotations::resolve));
      final var annotation = expr.typeAnnotation().ty();
      if (!Tys.containsInferred(annotation) || expr.resolvedTy() == null) {
        expr.resolvedTy(annotation);
      }
    }

    @Override
    public void visitAssignment(Hir.Assignment expr) {
      HirVisitor.super.visitAssignment(expr);

      if (HirExprs.isDeadEnd(expr.rhs(), expr.lhs())) {
        expr.ty(Ty.DEADEND);
      } else {
        expr.ty(Ty.VOID);
      }

      if (expr.lhs() instanceof Hir.Dec dec) {
        HirDeclarationTyping.resolveBinding(dec, expr.rhs().ty(), availableOnly);
      }
    }

    @Override
    public void visitCompoundAssignment(Hir.CompoundAssignment expr) {
      HirVisitor.super.visitCompoundAssignment(expr);

      if (HirExprs.isDeadEnd(expr.rhs(), expr.target())) {
        expr.ty(Ty.DEADEND);
      } else {
        expr.ty(Ty.VOID);
      }

      //expr.ty(Ty.VOID); //HirFlow.deadEndOrFirst(VOID_TY_EXPR, expr.rhs(), expr.target()));
    }

    @Override
    public void visitLoop(final Hir.Loop expr) {
      HirVisitor.super.visitLoop(expr);

      final var breakTypes = FindBreakTypesVisitor.find(expr);
      final var breakType = Tys.union(breakTypes.toArray(new Ty[0]));

      // TODO: Perhaps the loop return type should be the last expression in the loop body? Or require explicit break?
      if (HirExprs.isDeadEnd(expr.body())) {
        expr.ty(breakType);
      } else if (breakTypes.isEmpty()) {
        expr.ty(Ty.VOID);
      } else {
        expr.ty(breakType);
      }
    }

    @Override
    public void visitConditional(Hir.Conditional expr) {
      HirVisitor.super.visitConditional(expr);

      final var predicate = expr.predicate().ty();
      if (Tys.isDeadend(predicate)) {
        expr.ty(Ty.DEADEND);
      } else {
        final var pass = expr.pass() == null ? Ty.VOID : expr.pass().ty();
        final var fail = expr.fail() == null ? Ty.VOID : expr.fail().ty();
        expr.ty(Tys.union(pass, fail));
      }
    }

    @Override
    public void visitExpressions(Hir.Expressions expr) {
      HirVisitor.super.visitExpressions(expr);
      final var children = expr.children();
      if (HirExprs.isDeadEnd(children)) {
        // A sequence can finish only if every direct child can finish normally.
        expr.ty(Ty.DEADEND);
      } else {
        expr.ty(children.length == 0 ? Ty.VOID : children[children.length - 1].ty());
      }
    }

    @Override
    public void visitBlock(Hir.Block expr) {
      HirVisitor.super.visitBlock(expr);
      expr.ty(expr.children().ty());
      //      expr.ty(HirFlow.flowType(expr));
    }

    @Override
    public void visitProgram(Hir.Program expr) {
      HirVisitor.super.visitProgram(expr);
      expr.ty(HirBodyResultTyping.resolve(expr.expressions()));
    }

    @Override
    public void visitFunction(Hir.Function expr) {
      HirVisitor.super.visitFunction(expr);
      HirFunctionReturnTyping.resolve(expr);
    }

    @Override
    public void visitFunctionSignature(Hir.FunctionSignature expr) {
      HirVisitor.super.visitFunctionSignature(expr);
      expr.returnTypeAnnotation(expr.returnTypeAnnotation().resolve(annotations::resolve));

      expr.ty(HirFnTyVisitorPass.fnToTyFn(expr));
    }

    @Override
    public void visitCall(Hir.Call expr) {

      HirVisitor.super.visitCall(expr);

      Ty ty;
      final var signature = HirExprs.getCallableSignature(expr.target());
      if (signature != null) {
        ty = signature.returnTy();
      } else if (availableOnly || Tys.isInferred(expr.target().ty()) || Tys.isDeadend(expr.target().ty())) {
        ty = Ty.INFER;
      } else {
        log.error("A call is to a target that is not a function: {}", expr);
        ty = expr.target().ty();
      }
      if (Tys.isDeadend(expr.target().ty()) || ArrayUtils.any(expr.arguments(), it -> Tys.isDeadend(it.ty()))) {
        expr.ty(Ty.DEADEND);
      } else {
        expr.ty(ty);
      }
    }

    @Override
    public void visitArrayAccess(Hir.ArrayAccess expr) {
      HirVisitor.super.visitArrayAccess(expr);

      final var accessorTy = HirExprs.getIndexingAccessorTy(expr.accessor());
      final var targetTy = HirExprs.getIndexingReceiverTy(expr.target());

      if (availableOnly && Tys.isInferred(targetTy)) {
        expr.ty(Tys.withCompletion(
          HirExprs.isDeadEnd(expr.target(), expr.accessor()) ? Ty.DEADEND : Ty.INFER, Ty.INFER));
        return;
      }

      if ((Tys.isInferred(targetTy) || Tys.isDeadend(targetTy)) && Tys.isDeadend(expr.target().ty())) {
        expr.ty(Tys.withCompletion(Ty.DEADEND, Ty.INFER));
        return;
      }

      final var isRange = Tys.asStructural(accessorTy, TyValueArray.class) != null;

      final var ty = switch (targetTy) {
        // The below should not return array type if is range, it should return a slice, which is different.
        case TyValueArray arrayTy -> isRange ? arrayTy : arrayTy.elementType();
        case TyStruct tuple when tuple.tuple() -> {
          final Integer index;
          if (availableOnly) {
            index = HirTupleAccess.availableIndex(tuple, expr.accessor());
          } else {
            index = HirTupleAccess.index(tuple, expr.accessor());
          }
          yield index == null ? Ty.INFER : tuple.fields()[index].ty();
        }
        case null, default -> {
          if (!availableOnly) {
            throw new UnexpectedExpressionException(expr.target());
          }
          yield Ty.INFER;
        }
      };

      expr.ty(Tys.withCompletion(HirExprs.isDeadEnd(expr.target(), expr.accessor()) ? Ty.DEADEND : ty, ty));
    }

    @Override
    public void visitNot(Hir.Not expr) {
      HirVisitor.super.visitNot(expr);
      expr.ty(Tys.isDeadend(expr.expression().ty()) ? Ty.DEADEND : Ty.BOOLEAN);
    }

    @Override
    public void visitRange(Hir.Range expr) {
      HirVisitor.super.visitRange(expr);

      // Transfer payloads are not range bounds, but the resolved range still identifies a slice.
      final var common = Tys.getCommonDenominator(expr.lower().ty(), expr.higher().ty());
      final var commonTy = Objects.requireNonNullElse(common.ty(), Ty.INVALID);
      final var rangeTy = new TyValueArray(commonTy, null);
      expr.ty(Tys.withCompletion(HirExprs.isDeadEnd(expr.lower(), expr.higher()) ? Ty.DEADEND : rangeTy, rangeTy));
    }

    @Override
    public void visitStruct(Hir.Struct expr) {
      HirVisitor.super.visitStruct(expr);

      final var fields = new ArrayList<TyField>();

      for (final var decl : expr.declarations()) {

        final var name = decl.lexeme().name();
        final var ty = Objects.requireNonNull(decl.resolvedTy());

        fields.add(new TyField(name, ty));
      }

      final var ty = new TyStruct(fields.toArray(new TyField[0]));
      expr.ty(ty);
    }

    @Override
    public void visitNewByCtor(Hir.NewByCtor expr) {
      HirVisitor.super.visitNewByCtor(expr);
      final Ty type = Objects.requireNonNullElse(HirExprs.getConstructionTargetLayout(expr.target()), Ty.INFER);
      expr.ty(Tys.isDeadend(expr.target().ty()) || (expr.arguments() != null && Tys.isDeadend(expr.arguments().ty()))
        ? Ty.DEADEND : type);
    }

    @Override
    public void visitNewByBlock(Hir.NewByBlock expr) {
      HirVisitor.super.visitNewByBlock(expr);

      final var type = Objects.requireNonNullElse(HirExprs.getConstructionTargetLayout(expr.target()), Ty.INFER);
      expr.ty(Tys.isDeadend(expr.target().ty()) || HirExprs.isDeadEnd(expr.fields()) ? Ty.DEADEND : type);
    }

    @Override
    public void visitDotAccess(final Hir.DotAccess expr) {
      HirVisitor.super.visitDotAccess(expr);

      final var owner = HirExprs.getMemberReceiverLayout(expr.target());
      final var field = HirExprs.getField(owner, expr.name());
      if (!availableOnly && owner != null && field == null) {
        throw new IllegalArgumentException("Unknown field: %s".formatted(expr.name()));
      }
      if (!availableOnly && owner == null && !Tys.isInferred(expr.target().ty()) && !Tys.isDeadend(expr.target().ty())) {
        throw new UnexpectedExpressionException(expr.target());
      }
      final var memberTy = field == null ? Ty.INFER : field.ty();
      expr.ty(Tys.isDeadend(expr.target().ty()) ? Ty.DEADEND : memberTy);
    }
  }
}
