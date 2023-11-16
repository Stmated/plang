package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

/**
 * This should be removed in favor of a more agnostic AST stage, and convert brackets based on context in HIR stage.
 * This will require less backtracking, since we will not actually care what it is inside the AST stage.
 */
public record AstBracketAccess(
  AstExpression target,
  AstBracket accessor
) implements AstExpression {

  @Override
  public String toString() {
    return STR."\{target}[\{accessor}]";
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitBracketAccess(this);
  }
}
