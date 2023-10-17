package com.github.stmated.plang.hir;

import com.github.stmated.plang.ast.AstVisitor;
import com.github.stmated.plang.hir.model.HirExpression;
import com.github.stmated.plang.hir.model.HirNoOp;

public class AstToHirVisitor implements AstVisitor<HirExpression> {

  @Override
  public HirExpression aggregate(final HirExpression a, final HirExpression b) {
    throw new IllegalStateException("AstToHir does not support aggregation, implement missing expression translation");
  }

  @Override
  public HirExpression noValue() {
    return new HirNoOp();
  }
}
