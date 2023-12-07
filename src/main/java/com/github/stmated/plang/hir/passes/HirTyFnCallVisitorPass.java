package com.github.stmated.plang.hir.passes;

import com.github.stmated.plang.hir.Hir;
import com.github.stmated.plang.hir.HirVisitor;
import com.github.stmated.plang.ty.Ty;
import com.github.stmated.plang.ty.TyFn;
import com.github.stmated.plang.ty.util.Tys;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class HirTyFnCallVisitorPass {

  public static void pass(Hir.Expression expr) {

    final var visitor = new Visitor();
    expr.visit(visitor);
  }

  public static class Visitor implements HirVisitor {

    @Override
    public void visitCall(Hir.Call expr) {

//      if (Tys.isInferred(expr.ty())) {
//
//        Ty ty;
//        if (expr.target().ty() instanceof TyFn fn) {
//          ty = fn.returnTy();
//        } else {
//
//          log.error(STR."A call is to a target that is not a function: \{expr}");
//          ty = expr.target().ty();
//        }
//
//        expr.ty(ty);
//      }

      HirVisitor.super.visitCall(expr);
    }
  }
}
