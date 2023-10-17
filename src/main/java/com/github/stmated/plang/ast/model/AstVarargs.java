package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

/**
 * TODO: This is a weird node -- remove it and make it a boolean on some other node?
 */
public record AstVarargs() implements AstExpression {

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitVarargs(this);
  }
}
