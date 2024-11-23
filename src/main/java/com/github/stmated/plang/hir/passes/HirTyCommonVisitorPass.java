package com.github.stmated.plang.hir.passes;

import com.github.stmated.plang.exceptions.InvalidTypeConversionException;
import com.github.stmated.plang.exceptions.UnexpectedExpressionException;
import com.github.stmated.plang.hir.Hir;
import com.github.stmated.plang.hir.HirJavaUtil;
import com.github.stmated.plang.hir.HirVisitor;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyField;
import com.github.stmated.plang.ty.TyFn;
import com.github.stmated.plang.ty.TyStruct;
import com.github.stmated.plang.ty.TyValueArray;
import com.github.stmated.plang.ty.TyValueNumber;
import com.github.stmated.plang.ty.util.Tys;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class HirTyCommonVisitorPass {

  public static void pass(Hir.Expression expr) {

    final var visitor = new Visitor();
    expr.visit(visitor);
  }

  @RequiredArgsConstructor
  private static class Visitor implements HirVisitor {

    @Override
    public void visitParameter(Hir.Parameter expr) {
      HirVisitor.super.visitParameter(expr);
      expr.ty(expr.valueType().ty());
    }

    @Override
    public void visitBinaryOperation(Hir.BinaryOperation expr) {

      // Go deeper first, and resolve any nested binary operations.
      HirVisitor.super.visitBinaryOperation(expr);

      if (expr.kind().isPredicate()) {
        expr.ty(Ty.BOOLEAN);
      } else {

        final var lhst = expr.lhs().ty();
        final var rhst = expr.rhs().ty();

        if (lhst != null && rhst != null) {
          final var result = Tys.getCommonDenominator(lhst, rhst);
          if (result.ty() != null) {

            // Hopefully many binary operations are resolved by this.
            expr.ty(result.ty());
          }
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
              ((Hir.Literal) element).ty(vn);
            }
          }
        }

        if (Tys.isInferred(arrayElementTy)) {
          arrayElementTy = element.ty();
        } else {

          final var common = Tys.getCommonDenominator(arrayElementTy, element.ty());
          final var diffs = common.diffs();
          if (!Tys.isSizeCompatible(diffs)) {
            throw new InvalidTypeConversionException("Array types must be size-compatible", element.ty(), arrayElementTy);
          }
        }
      }

      Object literalValue = (expr.length() == null) ? null : HirJavaUtil.resolveLiteralValue(expr.length());
      Integer arrayLength = (literalValue == null) ? null : ((Number) literalValue).intValue();

      /*
      if (arrayElementTy instanceof TyValueNumber vn) {
        final var elementArray = expr.elements();
        for (var i = 0; i < elementArray.length; i++) {
          final var element = elementArray[i];
        }
      }
      */

      // The element ty can still be INFER -- it will be used for late type decisions.
      expr.ty(new TyValueArray(arrayElementTy, arrayLength).intern());
    }

    @Override
    public void visitDec(Hir.Dec expr) {
      HirVisitor.super.visitDec(expr);
    }

    @Override
    public void visitAssignment(Hir.Assignment expr) {
      HirVisitor.super.visitAssignment(expr);

      if (expr.lhs() instanceof Hir.Dec dec && Tys.isInferred(dec.valueType().ty())) {
        dec.valueType(new Hir.TyExpr(expr.rhs().ty()));
      }
    }

    @Override
    public void visitLoopBreak(Hir.LoopBreak expr) {
      HirVisitor.super.visitLoopBreak(expr);
      expr.ty(Ty.VOID);
    }

    @Override
    public void visitConditional(Hir.Conditional expr) {
      HirVisitor.super.visitConditional(expr);

      Ty ty;
      final var tyPass = expr.pass().ty();
      if (expr.fail() != null) {

        final var tyFail = expr.fail().ty();
        ty = Tys.union(tyPass, tyFail);

      } else {

        // There is only one branch. The result will be a union of T and Void.
        // Most likely this will mean that it is always unusable as an assignment.
        ty = Tys.union(tyPass, Ty.VOID);
      }

      expr.ty(ty);
    }

    @Override
    public void visitExpressions(Hir.Expressions expr) {
      HirVisitor.super.visitExpressions(expr);
      if (Tys.isInferred(expr.ty())) {

        final var children = expr.children();
        if (children.length > 0) {
          expr.ty(children[children.length -1].ty());
        }
      }
    }

    @Override
    public void visitBlock(Hir.Block expr) {
      HirVisitor.super.visitBlock(expr);
      if (Tys.isInferred(expr.ty())) {
        expr.ty(expr.children().ty());
      }
    }

    @Override
    public void visitFunction(Hir.Function expr) {
      HirVisitor.super.visitFunction(expr);

      final var signature = expr.signature();
      if (Tys.isInferred(signature.returnType().ty())) {

        final var returnTy = expr.body().ty();
        signature.returnType(new Hir.TyExpr(returnTy));

        if (signature.ty() == null) {
          final var newFnTy = HirFnTyVisitorPass.fnToTyFn(signature);
          signature.ty(newFnTy);
        } else if (signature.ty() instanceof TyFn tyFn) {
          signature.ty(tyFn.toBuilder().returnTy(returnTy).build());
        } else {
          throw new IllegalArgumentException("Ty of function signature should be '" + TyFn.class.getSimpleName() + "'");
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

      if (Tys.isInferred(expr.ty())) {

        Ty ty;
        if (expr.target().ty() instanceof TyFn fn) {
          ty = fn.returnTy();
        } else {

          log.error("A call is to a target that is not a function: " + expr);
          ty = expr.target().ty();
        }

        expr.ty(ty);
      }
    }

    @Override
    public void visitReturn(Hir.Return expr) {
      HirVisitor.super.visitReturn(expr);
      expr.ty(expr.expression().ty());
    }

    @Override
    public void visitArrayAccess(Hir.ArrayAccess expr) {
      HirVisitor.super.visitArrayAccess(expr);

      final var targetTy = expr.target().ty();
      final var accessorTy = expr.accessor().ty();

      final var isRange = switch (accessorTy) {
        case TyValueArray _ -> true;
        default -> false;
      };

      final var ty = switch (targetTy) {
        // The below should not return array type if is range, it should return a slice, which is different.
        case TyValueArray arrayTy -> isRange ? arrayTy : arrayTy.elementType();
        default -> throw new UnexpectedExpressionException(expr.target());
      };

      expr.ty(ty);
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
      if (expr.ty() == null) {
        expr.ty(expr.target().ty());
      }
    }

    @Override
    public void visitNewByBlock(Hir.NewByBlock expr) {
      HirVisitor.super.visitNewByBlock(expr);
      if (expr.ty() == null) {
        expr.ty(expr.target().ty());
      }
    }

    @Override
    public void visitPath(Hir.Path expr) {

      if (expr.elements() == null || expr.elements().length == 0) {
        expr.ty(Ty.INVALID);
        return;
      }

      final var elements = expr.elements();

      elements[0].visit(this);
      var pointer = elements[0].ty();

      for (var i = 1; i < elements.length; i++) {

        final var current = elements[i];

        switch (current) {
          case Hir.Lexeme lexeme -> {

            switch (pointer) {
              case TyStruct struct -> {

                final var field = Arrays.stream(struct.fields())
                  .filter(f -> f.name().equals(lexeme.name()))
                  .findFirst().orElseThrow();

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

            call.ty(pointer);
          }
          default -> throw new UnexpectedExpressionException(current);
        }
      }

      expr.ty(pointer);
    }
  }
}
