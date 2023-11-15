package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstSpread(AstExpression expression) implements AstExpression {

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitSpread(this);
  }

  @Override
  public String toString() {
    return STR."...\{expression}";
  }
}
