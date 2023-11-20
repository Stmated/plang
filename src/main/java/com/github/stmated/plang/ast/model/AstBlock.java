package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

import java.util.Objects;

public record AstBlock(AstExpression expression) implements AstExpression {

  @Override
  public String toString() {
    return Objects.toString(expression);
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitBlock(this);
  }
}
