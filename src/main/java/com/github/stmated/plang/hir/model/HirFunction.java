package com.github.stmated.plang.hir.model;

import com.github.stmated.plang.ty.Ty;

public record HirFunction(HirIdentifier identifier, HirParameter[] parameters, boolean vararg, Ty returnType) implements HirExpression {

//  @Override
//  public HirType getResultType() {
//    return this.returnType();
//  }
}
