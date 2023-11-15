package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

import com.github.stmated.plang.ty.TyValue;

/**
 * TODO: Probably not good, since it relies on the data types of Java and not the actual target language
 */
public record AstLiteral(
    String content,
    TyValue ty
) implements AstExpression {

  @Override
  public String toString() {
    return STR."\{content}:\{ty}";
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitLiteral(this);
  }
}
