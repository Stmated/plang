package com.github.stmated.plang.ast;

import com.github.stmated.plang.ast.visitor.AstVisitor;

import java.util.Objects;

/**
 * TODO: Probably not good, since it relies on the data types of Java and not the actual target language
 */
public record AstLiteral(
    Object value
) implements AstExpression {

  @Override
  public String toString() {
    return Objects.toString(value, "<null>");
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitLiteral(this);
  }
}
