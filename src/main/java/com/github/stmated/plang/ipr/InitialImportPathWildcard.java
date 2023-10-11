package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

public record InitialImportPathWildcard() implements InitialExpression, InitialImportCapable {
  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitImportPathWildcard(this);
  }
}
