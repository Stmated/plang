package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

/**
 * TODO: Remove this and instead convert directly to HirCall in AstToHir -- the AST should be more agnostic!
 *        Will help us in allowing strange and incorrect syntax to flow a bit further, so we can give better error messages when we know more info.
 *        It should work more like array access for HirArrayAccess!
 */
public record AstCall(
  AstExpression target,
  AstParen paren,
  boolean onErrorBubbleUp,
  boolean partial
) implements AstExpression {

  @Override
  public String toString() {
    return target +
      (partial ? "~" : "") +
      "(" + paren + ")" + (onErrorBubbleUp ? "!" : "");
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitCall(this);
  }
}
