package org.inf.hir.passes;

import jakarta.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirVisitor;
import org.inf.ty.Ty;
import org.inf.ty.TyFn;
import org.inf.ty.TyParam;

import java.util.Objects;

@UtilityClass
public class HirFnTyVisitorPass {

  public static void pass(Hir.Expression expr) {

    final var visitor = new Visitor();
    expr.visit(visitor);
  }

  public static class Visitor implements HirVisitor {

    @Override
    public void visitFunction(Hir.Function expr) {

//      if (expr.ty() == null) {
//
//        final var tyFn = fnToTyFn(expr.signature());
//        expr.ty(tyFn);
//      }

      HirVisitor.super.visitFunction(expr);
    }
  }

  @Nonnull
  public static TyFn fnToTyFn(Hir.FunctionSignature signature) {

    final var parameterTys = new TyParam[signature.parameters().length];
    for (var i = 0; i < signature.parameters().length; i++) {

      final var parameter = signature.parameters()[i];
      final var parameterName = parameter.lexeme().name();
      final var paramTy = parameter.valueType().ty();
      parameter.ty(paramTy);

      parameterTys[i] = new TyParam(parameterName, paramTy);
    }

    final var returnTy = Objects.requireNonNullElse(signature.returnType().ty(), Ty.INFER);
    return new TyFn(parameterTys, signature.vararg(), returnTy);
  }
}
