package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;

/// Links lambda parameters to resolved signatures at function-valued use sites.
@UtilityClass
public class HirFunctionContextualTypingVisitorPass {

  public static void pass(final Hir.Expression expression) {
    HirFunctionContextVisitor.visit(expression, HirFunctionTyping::contextualize);
  }
}
