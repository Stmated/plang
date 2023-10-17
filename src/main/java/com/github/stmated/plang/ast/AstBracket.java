package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

import java.util.Arrays;

public record AstBracket(AstExpression[] children) implements AstExpression {

  @Override
  public String toString() {
    return '[' + Arrays.toString(children) + ']';
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitBracket(this);
  }
}
