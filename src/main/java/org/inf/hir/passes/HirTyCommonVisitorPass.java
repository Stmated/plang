package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.exceptions.UnexpectedExpressionException;
import org.inf.hir.Hir;
import org.inf.hir.HirJavaUtil;
import org.inf.hir.HirTupleAccess;
import org.inf.hir.HirVisitor;
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
    expr.visit(new Visitor(false));
    return expr;
  }

  public static void resolveAvailableTypes(final Hir.Expression expression) {
    expression.visit(new Visitor(true));
  }

  private record Visitor(boolean availableOnly) implements HirVisitor {

    @Override
    public void visitTuple(Hir.Tuple expr) {
      HirVisitor.super.visitTuple(expr);
      HirTupleTyping.resolve(expr, availableOnly);
    }

    @Override
    public void visitParameter(Hir.Parameter expr) {
      HirVisitor.super.visitParameter(expr);
      final var annotation = expr.typeAnnotation().ty();
      if (!Tys.isInferred(annotation) || expr.resolvedTy() == null) {
        expr.resolvedTy(annotation);
      }
    }

    @Override
    public void visitBinaryOperation(Hir.BinaryOperation expr) {

      // Go deeper first, and resolve any nested binary operations.
      HirVisitor.super.visitBinaryOperation(expr);

      final var lhst = expr.lhs().ty();
      final var rhst = expr.rhs().ty();

      if (lhst == Ty.DEADEND) {
        expr.ty(Ty.DEADEND);
      } else if (!expr.kind().isShortCircuiting() && rhst == Ty.DEADEND) {
        expr.ty(Ty.DEADEND);
      } else if (expr.kind().isPredicate()) {
        expr.ty(Ty.BOOLEAN);
      } else {
        expr.ty(Objects.requireNonNullElse(Tys.getCommonDenominator(lhst, rhst).ty(), Ty.INFER));
      }
    }

    @Override
    public void visitArray(Hir.Array expr) {
      HirVisitor.super.visitArray(expr);

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

        final var elementTy = element.ty();
        if (elementTy == Ty.DEADEND) {
          continue;
        }
        if (Tys.isInferred(arrayElementTy)) {
          arrayElementTy = elementTy;
        } else if (!availableOnly && (TupleTypes.containsTuple(arrayElementTy) || TupleTypes.containsTuple(elementTy))) {
          if (!TypeComparison.sameValueType(elementTy, arrayElementTy)) {
            throw new InvalidTypeConversionException("Array tuple elements must have matching shapes and slot types", elementTy, arrayElementTy);
          }
        } else if (!availableOnly) {

          final var common = Tys.getCommonDenominator(arrayElementTy, elementTy);
          final var diffs = common.diffs();
          if (!Tys.isSizeCompatible(diffs)) {
            throw new InvalidTypeConversionException("Array types must be size-compatible", elementTy, arrayElementTy);
          }
        }
      }

      Integer arrayLength;
      if (expr.length() == null || expr.length().ty() == Ty.DEADEND) {
        arrayLength = null;
      } else {
        final var literalValue = HirJavaUtil.resolveLiteralValue(expr.length());
        arrayLength = (literalValue == null) ? null : ((Number) literalValue).intValue();
      }

        /*
        if (arrayElementTy instanceof TyValueNumber vn) {
          final var elementArray = expr.elements();
          for (var i = 0; i < elementArray.length; i++) {
            final var element = elementArray[i];
          }
        }
        */

      // The element ty can still be INFER -- it will be used for late type decisions.
      final var arrayTy = new TyValueArray(arrayElementTy, arrayLength).intern();
      expr.arrayTy(arrayTy);

      if (Tys.isDeadEnd(expr.elements()) || (expr.length() != null && expr.length().ty() == Ty.DEADEND)) {
        expr.ty(Ty.DEADEND);
      } else {
        expr.ty(arrayTy);
      }
    }

    @Override
    public void visitDec(Hir.Dec expr) {
      HirVisitor.super.visitDec(expr);
      final var annotation = expr.typeAnnotation() == null ? null : expr.typeAnnotation().ty();
      if (!Tys.containsInferred(annotation) || expr.resolvedTy() == null) {
        expr.resolvedTy(annotation);
      }
    }

    @Override
    public void visitAssignment(Hir.Assignment expr) {
      HirVisitor.super.visitAssignment(expr);

      if (Tys.isDeadEnd(expr.rhs(), expr.lhs())) {
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

      if (Tys.isDeadEnd(expr.rhs(), expr.target())) {
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
      if (Tys.isDeadEnd(expr.body())) {
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
      if (predicate == Ty.DEADEND) {
        expr.ty(predicate);
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
      if (Tys.isDeadEnd(children)) {
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

      expr.ty(HirFnTyVisitorPass.fnToTyFn(expr));
    }

    @Override
    public void visitCall(Hir.Call expr) {

      HirVisitor.super.visitCall(expr);

      Ty ty;
      final var signature = Tys.getCallableSignature(expr.target());
      if (signature != null) {
        ty = signature.returnTy();
      } else if (availableOnly || Tys.isInferred(expr.target().ty()) || expr.target().ty() == Ty.DEADEND) {
        ty = Ty.INFER;
      } else {
        log.error("A call is to a target that is not a function: {}", expr);
        ty = expr.target().ty();
      }
      if (expr.target().ty() == Ty.DEADEND || ArrayUtils.any(expr.arguments(), it -> it.ty() == Ty.DEADEND)) {
        expr.ty(Ty.DEADEND);
      } else {
        expr.ty(ty);
      }
    }

    @Override
    public void visitArrayAccess(Hir.ArrayAccess expr) {
      HirVisitor.super.visitArrayAccess(expr);

      final var accessorTy = Tys.getIndexingAccessorTy(expr.accessor());
      final var targetTy = Tys.getIndexingReceiverTy(expr.target());

      if (availableOnly && Tys.isInferred(targetTy)) {
        expr.indexedTy(Ty.INFER);
        expr.ty(Tys.isDeadEnd(expr.target(), expr.accessor()) ? Ty.DEADEND : Ty.INFER);
        return;
      }

      if ((Tys.isInferred(targetTy) || targetTy == Ty.DEADEND) && expr.target().ty() == Ty.DEADEND) {
        expr.indexedTy(Ty.INFER);
        expr.ty(Ty.DEADEND);
        return;
      }

      final var isRange = accessorTy instanceof TyValueArray;

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

      expr.indexedTy(ty);
      expr.ty(Tys.isDeadEnd(expr.target(), expr.accessor()) ? Ty.DEADEND : ty);
    }

    @Override
    public void visitNot(Hir.Not expr) {
      HirVisitor.super.visitNot(expr);
      expr.ty(expr.expression().ty() == Ty.DEADEND ? Ty.DEADEND : Ty.BOOLEAN);
    }

    @Override
    public void visitRange(Hir.Range expr) {
      HirVisitor.super.visitRange(expr);

      // Transfer payloads are not range bounds, but the resolved range still identifies a slice.
      final var common = Tys.getCommonDenominator(expr.lower().ty(), expr.higher().ty());
      final var commonTy = Objects.requireNonNullElse(common.ty(), Ty.INVALID);
      final var rangeTy = new TyValueArray(commonTy, null);
      expr.rangeTy(rangeTy);
      if (Tys.isDeadEnd(expr.lower(), expr.higher())) {
        expr.ty(Ty.DEADEND);
      } else {
        expr.ty(rangeTy);
      }
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
      final Ty type = Objects.requireNonNullElse(Tys.getConstructionTargetTy(expr.target()), Ty.INFER);
      expr.ty(expr.target().ty() == Ty.DEADEND || (expr.arguments() != null && expr.arguments().ty() == Ty.DEADEND)
        ? Ty.DEADEND : type);
    }

    @Override
    public void visitNewByBlock(Hir.NewByBlock expr) {
      HirVisitor.super.visitNewByBlock(expr);
      final Ty type = Objects.requireNonNullElse(Tys.getConstructionTargetTy(expr.target()), Ty.INFER);
      expr.ty(expr.target().ty() == Ty.DEADEND || Tys.isDeadEnd(expr.fields()) ? Ty.DEADEND : type);
    }

    @Override
    public void visitPath(Hir.Path expr) {

      if (expr.elements() == null || expr.elements().length == 0) {
        expr.memberTy(Ty.INVALID);
        expr.ty(Ty.INVALID);
        return;
      }

      final var elements = expr.elements();

      visitChild(elements[0]);
      final var receiver = Tys.getMemberReceiverTy(elements[0]);
      Ty pointer = receiver == null ? elements[0].ty() : receiver;

      if (availableOnly && Tys.isInferred(pointer)) {
        expr.memberTy(Ty.INFER);
        expr.ty(elements[0].ty() == Ty.DEADEND ? Ty.DEADEND : Ty.INFER);
        return;
      }

      if ((Tys.isInferred(pointer) || pointer == Ty.DEADEND) && elements[0].ty() == Ty.DEADEND) {
        expr.memberTy(Ty.INFER);
        expr.ty(Ty.DEADEND);
        return;
      }

      for (var i = 1; i < elements.length; i++) {

        final var current = elements[i];
        if (availableOnly && Tys.isInferred(pointer)) {
          expr.memberTy(Ty.INFER);
          expr.ty(elements[0].ty() == Ty.DEADEND ? Ty.DEADEND : Ty.INFER);
          return;
        }

        switch (current) {
          case Hir.Lexeme lexeme -> {

            switch (pointer) {
              case TyStruct struct -> {

                final var fieldTy = Tys.getStructFieldTy(struct, lexeme.name());
                if (fieldTy == null && !availableOnly) {
                  throw new IllegalArgumentException("Unknown field: %s".formatted(lexeme.name()));
                }
                pointer = Objects.requireNonNullElse(fieldTy, Ty.INFER);
                lexeme.ty(pointer);
              }
              default -> {
                if (!availableOnly) {
                  throw new UnexpectedExpressionException(current);
                }
                pointer = Ty.INFER;
              }
            }
          }
          case Hir.Call call -> {

            // TODO: Make this work, even if ugly! :)
            pointer = switch (call.target()) {
              case Hir.Identifier id -> switch (id.lexeme().name()) {
                case "toString" -> Ty.STRING;
                default -> throw new UnexpectedExpressionException(id);
              };
              default -> throw new UnexpectedExpressionException(call.target());
            };

            HirVisitor.super.visitCall(call);
            call.ty(ArrayUtils.any(call.arguments(), it -> it.ty() == Ty.DEADEND)
              || call.target().ty() == Ty.DEADEND ? Ty.DEADEND : pointer);
          }
          default -> throw new UnexpectedExpressionException(current);
        }
      }

      expr.memberTy(pointer);
      expr.ty(Tys.isDeadEnd(elements) ? Ty.DEADEND : pointer);
    }
  }
}
