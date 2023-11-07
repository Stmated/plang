package com.github.stmated.plang.hir.model;

import com.github.stmated.plang.ty.Ty;

public record HirParameter(HirIdentifier identifier, Ty ty) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//    return this.type();
//  }
}
