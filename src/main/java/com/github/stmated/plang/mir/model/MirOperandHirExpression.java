package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.hir.model.HirExpression;

/**
 * TODO: Remove someday, to separate the HIR from the MIR
 */
public record MirOperandHirExpression(HirExpression hir) implements MirOperand {

  @Override
  public String toString() {
    return STR."{HIR: \{hir}}";
  }
}
