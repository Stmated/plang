package com.github.stmated.plang.hir.model;

import java.util.Objects;
import org.codehaus.commons.nullanalysis.Nullable;

/**
 * Q: Is this a concept appropriate for the HIR, or should it be a label jump?
 *        Are there benefits to being able to represent a "break" further down the chain?
 */
public record HirLoopBreak(
  @Nullable
  HirExpression value
) implements HirExpression {

  @Override
  public String toString() {
    return STR."break\{this.value() == null ? "" : STR." \{this.value()}"}";
  }
}
