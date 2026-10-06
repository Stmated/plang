package org.inf.hir.passes;

import lombok.RequiredArgsConstructor;
import org.inf.hir.Hir;
import org.inf.hir.HirTransformer;
import org.inf.util.ArrayUtils;

import java.util.Arrays;

/// Turn indexed and spread-call members into operations on the complete field path before resolving names.
public final class HirIndexedPathTransformerPass {

  private HirIndexedPathTransformerPass() {
  }

  public static Hir.Expression pass(Hir.Expression expression) {
    return expression.transform(new Transformer());
  }

  private static Hir.Path append(Hir.Expression target, Hir.Expression field) {
    if (target instanceof Hir.Path path) {
      final var elements = Arrays.copyOf(path.elements(), path.elements().length + 1);
      elements[elements.length - 1] = field;
      return new Hir.Path(elements, null, null);
    }
    return new Hir.Path(new Hir.Expression[]{target, field}, null, null);
  }

  private static final class Transformer implements HirTransformer {

    @Override
    public Hir.Expression transformPath(Hir.Path expression) {
      HirTransformer.super.transformPath(expression);
      final var elements = expression.elements();
      if (Arrays.stream(elements).skip(1).noneMatch(element -> element instanceof Hir.ArrayAccess || isSpreadCall(element))) {
        return expression;
      }
      var target = elements[0];
      for (var i = 1; i < elements.length; i++) {
        if (elements[i] instanceof Hir.ArrayAccess || isSpreadCall(elements[i])) {
          target = elements[i].transform(new MemberTargetTransformer(target));
        } else {
          target = append(target, elements[i]);
        }
      }
      return target;
    }

    private static boolean isSpreadCall(final Hir.Expression expression) {
      return expression instanceof Hir.Call call
        && ArrayUtils.any(call.arguments(), argument -> argument.value() instanceof Hir.Spread);
    }
  }

  @RequiredArgsConstructor
  private static final class MemberTargetTransformer implements HirTransformer {

    private final Hir.Expression target;

    @Override
    public Hir.Expression transformArrayAccess(Hir.ArrayAccess expression) {
      // Only the member target belongs to the path; index expressions keep their own scope.
      expression.target(expression.target().transform(this));
      return expression;
    }

    @Override
    public Hir.Expression transformCall(final Hir.Call expression) {
      expression.target(expression.target().transform(this));
      return expression;
    }

    @Override
    public Hir.Expression transformLexeme(Hir.Lexeme expression) {
      return append(target, expression);
    }
  }
}
