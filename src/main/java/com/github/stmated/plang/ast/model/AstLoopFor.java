package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

public record AstLoopFor(
  AstExpression head,
  AstExpression block

//    AstAssignment[] assignments,
//    AstExpression predicate,
//    AstExpression[] steppers,
//    AstBlock block
) implements AstExpression {

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitLoopFor(this);
  }
}
