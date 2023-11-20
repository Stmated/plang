package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstNew(
    AstExpression target,
    AstIdentifier allocator,
    AstExpression arguments
) implements AstExpression {

  @Override
  public String toString() {
    return STR."new \{allocator} \{target}(\{arguments})";
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitNew(this);
  }
}
