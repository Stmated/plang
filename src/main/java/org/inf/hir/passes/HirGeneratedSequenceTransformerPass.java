package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.HirTransformer;
import org.inf.ty.util.Tys;

import java.util.Arrays;

/** Removes unreachable generated tails after their expressions have been typed. */
@UtilityClass
public class HirGeneratedSequenceTransformerPass {

  public static Hir.Expression pass(Hir.Expression expression) {
    return expression.transform(new Transformer());
  }

  private static class Transformer implements HirTransformer {

    @Override
    public Hir.Expression transformExpressions(Hir.Expressions expression) {
      HirTransformer.super.transformExpressions(expression);
      if (expression.generated()) {
        final var children = expression.children();
        for (var i = 0; i + 1 < children.length; i++) {

          if (Tys.isDeadend(children[i].ty())) {
            expression.children(Arrays.copyOf(children, i + 1));
            break;
          }
        }
      }
      return expression;
    }
  }
}
