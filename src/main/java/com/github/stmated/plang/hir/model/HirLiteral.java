package com.github.stmated.plang.hir.model;

import com.github.stmated.plang.ty.TyValue;

public record HirLiteral(String content, TyValue ty) implements HirExpression {

  @Override
  public String toString() {
    return STR."\{content}: \{ty.toShortString()}";
  }
}
