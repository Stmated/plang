package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

public record AstBecome(AstCall call) implements AstExpression {
  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitBecome(this);
  }
}
