package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialImportPathGroup(
    InitialImportCapable[] items
) implements InitialExpression, InitialImportCapable {
  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitImportPathGroup(this);
  }
}
