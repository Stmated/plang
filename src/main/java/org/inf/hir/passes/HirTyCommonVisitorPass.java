package org.inf.hir.passes;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.inf.exceptions.InvalidTypeConversionException;
import org.inf.exceptions.UnexpectedExpressionException;
import org.inf.hir.Hir;
import org.inf.hir.HirJavaUtil;
import org.inf.hir.HirVisitor;
import org.inf.ty.*;
import org.inf.ty.util.Tys;
import org.inf.ty.util.TupleTypes;
import org.inf.ty.util.TypeComparison;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@UtilityClass
public class HirTyCommonVisitorPass {

  public static <T extends Hir.Expression> T pass(T expr) {

    final var visitor = new Visitor();
    expr.visit(visitor);
    return expr;
  }

  @RequiredArgsConstructor
  private static class Visitor implements HirVisitor {

    @Override
    public void visitTuple(Hir.Tuple expr) {
      HirVisitor.super.visitTuple(expr);
      HirTupleTyping.resolve(expr);
    }

    @Override
    public void visitTupleEntry(Hir.TupleEntry expr) {
      HirVisitor.super.visitTupleEntry(expr);
      if (expr.label() != null) {
        throw new IllegalArgumentException("Named and mixed tuples are not supported yet");
      }
    }

    @Override
    public void visitParameter(Hir.Parameter expr) {
      HirVisitor.super.visitParameter(expr);
      expr.ty(expr.valueType().ty());
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

      if (Tys.isInferred(expr.valueTy())) {

        // Hopefully many binary operations are resolved by this.
        if (expr.kind().isPredicate()) {
          expr.valueTy(Ty.BOOLEAN);
        } else {
          expr.valueTy(Tys.union(expr.lhs().valueTy(), expr.rhs().valueTy()));
        }
      }
    }

    @Override
    public void visitArray(Hir.Array expr) {
      HirVisitor.super.visitArray(expr);

      var arrayElementTy = expr.elementType().ty();
      for (final var element : expr.elements()) {

        if (arrayElementTy instanceof TyValueNumber vn) {
          if (element instanceof Hir.Literal lit && lit.ty() instanceof TyValueNumber litTy_n) {
            if (!litTy_n.width().explicit() && (litTy_n.width().value() != vn.width().value() || litTy_n.signed() != vn.signed())) {

              // val array = [1, 2, 3; uint8]
              // val array = [1, 2, 3; uint]
              // Should automatically translate non-explicit integers in array to stated type.
              //elementArray[i] = new Hir.Literal(lit.content(), vn);
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
        } else if (TupleTypes.containsTuple(arrayElementTy) || TupleTypes.containsTuple(elementTy)) {
          if (!TypeComparison.sameValueType(elementTy, arrayElementTy)) {
            throw new InvalidTypeConversionException("Array tuple elements must have matching shapes and slot types", elementTy, arrayElementTy);
          }
        } else {

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
      expr.valueTy(new TyValueArray(arrayElementTy, arrayLength).intern());

      if (Tys.isDeadEnd(expr.elements()) || (expr.length() != null && expr.length().ty() == Ty.DEADEND)) {
        expr.ty(Ty.DEADEND);
      } else {
        expr.ty(expr.valueTy());
      }
    }

    @Override
    public void visitDec(Hir.Dec expr) {
      HirVisitor.super.visitDec(expr);
    }

    @Override
    public void visitAssignment(Hir.Assignment expr) {
      HirVisitor.super.visitAssignment(expr);

      if (Tys.isDeadEnd(expr.rhs(), expr.lhs())) {
        expr.ty(Ty.DEADEND);
      } else {
        // TODO: This should likely be the RHS type, to make assignment behave more like an expression with result type
        expr.ty(Ty.VOID);
      }

      expr.valueTy(expr.rhs().valueTy());

      if (expr.lhs() instanceof Hir.Dec dec && Tys.isInferred(dec.valueType().ty())) {
        dec.valueType(new Hir.TyExpr(expr.rhs().ty()));
      }
    }

    @Override
    public void visitCompoundAssignment(Hir.CompoundAssignment expr) {
      HirVisitor.super.visitCompoundAssignment(expr);

      if (Tys.isDeadEnd(expr.rhs(), expr.target())) {
        expr.ty(Ty.DEADEND);
      } else {
        // TODO: This should likely be the RHS type, to make assignment behave more like an expression with result type
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
        expr.valueTy(breakType == Ty.DEADEND ? Ty.VOID : breakType);
      } else if (breakTypes.isEmpty()) {
        expr.ty(Ty.VOID);
        expr.valueTy(expr.ty());
      } else {
        expr.ty(breakType);
        expr.valueTy(expr.ty());
      }
    }

    @Override
    public void visitConditional(Hir.Conditional expr) {
      HirVisitor.super.visitConditional(expr);

      if (Tys.isInferred(expr.ty())) {

        expr.valueTy(Tys.union(
          expr.pass() == null ? Ty.VOID : expr.pass().valueTy(),
          expr.fail() == null ? Ty.VOID : expr.fail().valueTy()
        ));
        final var predicate = expr.predicate().ty();
        if (predicate == Ty.DEADEND) {
          expr.ty(predicate);
        } else {
          final var pass = expr.pass() == null ? Ty.VOID : expr.pass().ty();
          final var fail = expr.fail() == null ? Ty.VOID : expr.fail().ty();
          expr.ty(Tys.union(pass, fail));
        }
      }
    }

    @Override
    public void visitExpressions(Hir.Expressions expr) {
      HirVisitor.super.visitExpressions(expr);
      if (Tys.isInferred(expr.ty())) {

        final var children = expr.children();
        if (Tys.isDeadEnd(children)) {

          // A sequence can finish only if every direct child can finish normally.
          expr.ty(Ty.DEADEND);
        } else {
          expr.ty(children.length == 0 ? Ty.VOID : children[children.length - 1].ty());
        }
      }
    }

    @Override
    public void visitBlock(Hir.Block expr) {
      HirVisitor.super.visitBlock(expr);
      if (Tys.isInferred(expr.ty())) {
        expr.ty(expr.children().ty());
      }
//      expr.ty(HirFlow.flowType(expr));
    }

    @Override
    public void visitProgram(Hir.Program expr) {
      HirVisitor.super.visitProgram(expr);

      if (Tys.isInferred(expr.ty())) {

        final var visitor = new FindReturnTypesVisitor();
        expr.visit(visitor);

        final var retTy = visitor.returnType();
        final var childTy = expr.expressions().valueTy();

        // For a program, return type is an explicit return or or the child's value type.
        expr.ty(Objects.requireNonNullElse(retTy, childTy));
      }

      //expr.ty(HirFlow.returnType(expr.expressions()));
    }

    @Override
    public void visitFunction(Hir.Function expr) {
      HirVisitor.super.visitFunction(expr);

      final var signature = expr.signature();
      if (Tys.isInferred(signature.returnType().ty())) {

        final var visitor = new FindReturnTypesVisitor();
        expr.body().visit(visitor);

        // For a program, return type is an explicit return or or the child's value type.
        final var returnTy = Objects.requireNonNullElse(
          visitor.returnType(),
          expr.body().valueTy()
        );

        //final var returnTy = HirFlow.returnType(expr.body());
        signature.returnType(new Hir.TyExpr(returnTy));

        if (signature.ty() == null) {
          final var newFnTy = HirFnTyVisitorPass.fnToTyFn(signature);
          signature.ty(newFnTy);
        } else {
          final var tyFn = signature.ty();
          signature.ty(tyFn.toBuilder().returnTy(returnTy).build());
        }
      }
    }

    @Override
    public void visitFunctionSignature(Hir.FunctionSignature expr) {
      HirVisitor.super.visitFunctionSignature(expr);

      if (expr.ty() == null) {

        final var newFnTy = HirFnTyVisitorPass.fnToTyFn(expr);
        expr.ty(newFnTy);
      }
    }

    @Override
    public void visitCall(Hir.Call expr) {

      HirVisitor.super.visitCall(expr);

      if (Tys.isInferred(expr.valueTy())) {

        Ty ty;
        if (expr.target().valueTy() instanceof TyFn fn) {
          ty = fn.returnTy();
        } else if (expr.target().ty() == Ty.DEADEND) {
          ty = Ty.INFER;
        } else {

          log.error("A call is to a target that is not a function: {}", expr);
          ty = expr.target().ty();
        }

        expr.valueTy(ty);
      }

      if (expr.target().ty() == Ty.DEADEND || Tys.isDeadEnd(expr.arguments())) {
        expr.ty(Ty.DEADEND);
      } else {
        expr.ty(expr.valueTy());
      }
    }

//    @Override
//    public void visitReturn(Hir.Return expr) {
//      HirVisitor.super.visitReturn(expr);
//      expr.ty(Ty.DEADEND);
//    }

    @Override
    public void visitArrayAccess(Hir.ArrayAccess expr) {
      HirVisitor.super.visitArrayAccess(expr);

      final var accessorTy = expr.accessor().valueTy();
      final var targetTy = expr.target().valueTy();

      if ((Tys.isInferred(targetTy) || targetTy == Ty.DEADEND) && expr.target().ty() == Ty.DEADEND) {
        expr.valueTy(Ty.INFER);
        expr.ty(Ty.DEADEND);
        return;
      }

      final var isRange = switch (accessorTy) {
        case TyValueArray _ -> true;
        default -> false;
      };

      final var ty = switch (targetTy) {
        // The below should not return array type if is range, it should return a slice, which is different.
        case TyValueArray arrayTy -> isRange ? arrayTy : arrayTy.elementType();
        default -> throw new UnexpectedExpressionException(expr.target());
      };

      expr.valueTy(ty);
      expr.ty(Tys.isDeadEnd(expr.target(), expr.accessor()) ? Ty.DEADEND : ty);
    }

    @Override
    public void visitNot(Hir.Not expr) {
      HirVisitor.super.visitNot(expr);
      expr.valueTy(Ty.BOOLEAN);
      expr.ty(expr.expression().ty() == Ty.DEADEND ? Ty.DEADEND : Ty.BOOLEAN);
    }

    @Override
    public void visitRange(Hir.Range expr) {
      HirVisitor.super.visitRange(expr);

      if (Tys.isDeadEnd(expr.lower(), expr.higher())) {
        expr.ty(Ty.DEADEND);
      } else {
        final var common = Tys.getCommonDenominator(expr.lower().ty(), expr.higher().ty());
        final var commonTy = Objects.requireNonNullElse(common.ty(), Ty.INVALID);
        expr.ty(new TyValueArray(commonTy, null));
      }

      final var valueCommon = Tys.getCommonDenominator(expr.lower().helpfulTy(), expr.higher().helpfulTy());
      final var valueCommonTy = Objects.requireNonNullElse(valueCommon.ty(), Ty.INVALID);
      expr.valueTy(new TyValueArray(valueCommonTy, null));
    }

    @Override
    public void visitStruct(Hir.Struct expr) {
      HirVisitor.super.visitStruct(expr);

      final var fields = new ArrayList<TyField>();

      for (final var decl : expr.declarations()) {

        final var name = decl.lexeme().name();
        final var ty = Objects.requireNonNull(decl.valueType().ty()); // investigate_type_expression(decl.valueType());

        fields.add(new TyField(name, ty));
      }

      final var ty = new TyStruct(fields.toArray(new TyField[0]));
      expr.ty(ty);
    }

    @Override
    public void visitNewByCtor(Hir.NewByCtor expr) {
      HirVisitor.super.visitNewByCtor(expr);
      if (Tys.isInferred(expr.valueTy())) {
        expr.valueTy(expr.target().valueTy());
      }
      expr.ty(expr.target().ty() == Ty.DEADEND || (expr.arguments() != null && expr.arguments().ty() == Ty.DEADEND)
        ? Ty.DEADEND : expr.valueTy());
    }

    @Override
    public void visitNewByBlock(Hir.NewByBlock expr) {
      HirVisitor.super.visitNewByBlock(expr);
      if (Tys.isInferred(expr.valueTy())) {
        expr.valueTy(expr.target().valueTy());
      }
      expr.ty(expr.target().ty() == Ty.DEADEND || Tys.isDeadEnd(expr.fields()) ? Ty.DEADEND : expr.valueTy());
    }

    @Override
    public void visitPath(Hir.Path expr) {

      if (expr.elements() == null || expr.elements().length == 0) {
        expr.ty(Ty.INVALID);
        return;
      }

      final var elements = expr.elements();

      elements[0].visit(this);
      var pointer = elements[0].valueTy();

      if ((Tys.isInferred(pointer) || pointer == Ty.DEADEND) && elements[0].ty() == Ty.DEADEND) {
        expr.valueTy(Ty.INFER);
        expr.ty(Ty.DEADEND);
        return;
      }

      for (var i = 1; i < elements.length; i++) {

        final var current = elements[i];

        switch (current) {
          case Hir.Lexeme lexeme -> {

            switch (pointer) {
              case TyStruct struct -> {

                final var field = Arrays.stream(struct.fields())
                  .filter(f -> lexeme.name().equals(f.name()))
                  .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown field: " + lexeme.name()));

                pointer = field.ty();
                lexeme.ty(pointer);
              }
              default -> throw new UnexpectedExpressionException(current);
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
            call.valueTy(pointer);
            call.ty(Tys.isDeadEnd(call.arguments()) || call.target().ty() == Ty.DEADEND ? Ty.DEADEND : pointer);
          }
          default -> throw new UnexpectedExpressionException(current);
        }
      }

      expr.valueTy(pointer);
      expr.ty(Tys.isDeadEnd(elements) ? Ty.DEADEND : pointer);
    }
  }
}
