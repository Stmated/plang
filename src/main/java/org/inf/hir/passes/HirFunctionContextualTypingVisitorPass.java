package org.inf.hir.passes;

import lombok.experimental.UtilityClass;
import org.inf.hir.Hir;
import org.inf.hir.typing.HirFunctionTyping;

/// Links lambda parameters to resolved signatures at function-valued use sites.
@UtilityClass
public class HirFunctionContextualTypingVisitorPass {

  public static void pass(final Hir.Expression expression) {
    HirInferredFunctionSignatureVisitor.visit(expression, HirFunctionTyping::adhereToConstraint);
  }
}
