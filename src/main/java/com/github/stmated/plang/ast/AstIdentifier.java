package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

public record AstIdentifier(String name) implements AstExpression {

  @Override
  public String toString() {
    return "[" + name + "]";
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitIdentifier(this);
  }
}
