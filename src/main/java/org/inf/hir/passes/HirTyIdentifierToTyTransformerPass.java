package org.inf.hir.passes;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.inf.hir.DynamicTy;
import org.inf.hir.DynamicTyDefault;
import org.inf.hir.DynamicTyRedirector;
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
    public DynamicTy transformDecType(DynamicTy expr) {
      return transformAnnotation(expr);
    }

    @Override
    public DynamicTy transformParameterType(DynamicTy expr) {
      return transformAnnotation(expr);
    }

    @Override
    public DynamicTy transformFunctionSignatureReturnType(DynamicTy expr) {
      return transformAnnotation(expr);
    }

    @Override
    public DynamicTy<? extends DynamicTy<?>> transformArrayElementType(DynamicTy<? extends DynamicTy<?>> expr) {
      return transformAnnotation(expr);
    }

    private DynamicTy<? extends DynamicTy<?>> transformAnnotation(final DynamicTy<? extends DynamicTy<?>> annotation) {
      try {
        typeModeCounter++;
        final var transformed = annotation.transform(this);
        if (transformed.expression() instanceof Hir.BuiltInTy builtIn) {
          return new DynamicTyRedirector(builtIn);
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
