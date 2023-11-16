package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;
import org.codehaus.commons.nullanalysis.Nullable;

public record AstRest() implements AstExpression {

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitRest(this);
  }

  @Override
  public String toString() {
    return "...";
  }
}
