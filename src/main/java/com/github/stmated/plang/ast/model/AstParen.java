package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;
import org.codehaus.commons.nullanalysis.Nullable;

public record AstParen(
  @Nullable
  AstExpression expression
) implements AstExpression {

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitParen(this);
  }
}
