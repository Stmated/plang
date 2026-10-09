package org.inf.hir.passes;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirTransformer;
import org.inf.ty.util.MachineTarget;

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
    public Hir.DynamicTy transformDecType(Hir.DynamicTy expr) {
      return transformAnnotation(expr);
    }

    @Override
    public Hir.DynamicTy transformParameterType(Hir.DynamicTy expr) {
      return transformAnnotation(expr);
    }

    @Override
    public Hir.DynamicTy transformFunctionSignatureReturnType(Hir.DynamicTy expr) {
      return transformAnnotation(expr);
    }

    @Override
    public Hir.DynamicTy transformArrayElementType(Hir.DynamicTy expr) {
      return transformAnnotation(expr);
    }

    private Hir.DynamicTy transformAnnotation(final Hir.DynamicTy annotation) {
      try {
        typeModeCounter++;
        final var transformed = annotation.transform(this);
        if (transformed.expression() instanceof Hir.BuiltInTy builtIn) {
          return new Hir.DynamicTy(builtIn.ty());
        }
        return transformed;
      } finally {
        typeModeCounter--;
      }
    }

    @Override
    public Hir.Expression transformIdentifier(Hir.Identifier expr) {

      if (typeModeCounter > 0 && expr.ty() == null) {

        final var builtIn = Hir.BuiltInTy.fromString(expr.name(), machineTarget);
        if (builtIn != null) {
          return builtIn;
        }
      }

      return HirTransformer.super.transformIdentifier(expr);
    }
  }
}
