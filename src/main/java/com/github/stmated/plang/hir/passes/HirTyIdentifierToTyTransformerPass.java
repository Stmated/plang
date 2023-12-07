package com.github.stmated.plang.hir.passes;

import com.github.stmated.plang.hir.Hir;
import com.github.stmated.plang.hir.HirTransformer;
import com.github.stmated.plang.ty.util.MachineTarget;
import com.github.stmated.plang.ty.util.Tys;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

@UtilityClass
public class HirTyIdentifierToTyTransformerPass {

  public static Hir.Expression pass(Hir.Expression expr, MachineTarget machineTarget) {

    final var transformer = new Transformer(machineTarget);
    return expr.transform(transformer);
  }

  @RequiredArgsConstructor
  private static class Transformer implements HirTransformer {

    private final MachineTarget machineTarget;
    private int typeModeCounter;

    @Override
    public Hir.Expression transformDecType(Hir.Expression expr) {
      try {
        typeModeCounter++;
        return HirTransformer.super.transformDecType(expr);
      } finally {
        typeModeCounter--;
      }
    }

    @Override
    public Hir.Expression transformParameterType(Hir.Expression expr) {
      try {
        typeModeCounter++;
        return HirTransformer.super.transformParameterType(expr);
      } finally {
        typeModeCounter--;
      }
    }

    @Override
    public Hir.Expression transformFunctionSignatureReturnType(Hir.Expression expr) {
      try {
        typeModeCounter++;
        return HirTransformer.super.transformFunctionSignatureReturnType(expr);
      } finally {
        typeModeCounter--;
      }
    }

    @Override
    public Hir.Expression transformArrayElementType(Hir.Expression expr) {
      try {
        typeModeCounter++;
        return HirTransformer.super.transformArrayElementType(expr);
      } finally {
        typeModeCounter--;
      }
    }

    @Override
    public Hir.Expression transformIdentifier(Hir.Identifier expr) {

      if (typeModeCounter > 0 && expr.ty() == null) {

        final var ty = Tys.fromString(expr.lexeme().name(), machineTarget);
        if (ty != null) {
          return new Hir.TyExpr(ty);
        }
      }

      return HirTransformer.super.transformIdentifier(expr);
    }
  }
}
