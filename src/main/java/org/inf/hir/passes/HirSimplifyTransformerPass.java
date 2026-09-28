package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirTransformer;

@UtilityClass
public class HirSimplifyTransformerPass {

  public static Hir.Expression pass(Hir.Expression expr) {

    final var transformer = new Transformer();
    return expr.transform(transformer);
  }

  private static class Transformer implements HirTransformer {

    @Override
    public Hir.Expression transformExpressions(Hir.Expressions expr) {
      if (expr.children().length == 1) {
        return expr.children()[0].transform(this);
      }

      return HirTransformer.super.transformExpressions(expr);
    }

    @Override
    public Hir.Expression transformBlock(Hir.Block expr) {
      return HirTransformer.super.transformBlock(expr);
    }
  }
}
