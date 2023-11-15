package com.github.stmated.plang.hir.model;

import com.github.stmated.plang.ty.Ty;

public record HirTy(Ty ty) implements HirExpression {

  @Override
  public String toString() {
    return ty.toShortString();
  }
}
