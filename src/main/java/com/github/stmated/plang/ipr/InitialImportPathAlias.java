package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialImportPathAlias(
    InitialIdentifier alias,
    InitialImportCapable target
) implements InitialExpression, InitialImportCapable {
  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitImportPathAlias(this);
  }
}
