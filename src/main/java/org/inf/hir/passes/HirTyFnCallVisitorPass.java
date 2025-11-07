package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;

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
