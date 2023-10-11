package com.github.stmated.plang.ipr;

import com.github.stmated.plang.ipr.visitor.InitialVisitor;

import java.util.Objects;

/**
 * TODO: Probably not good, since it relies on the data types of Java and not the actual target language
 */
public record InitialLiteral(
    Object value
) implements InitialExpression {

  @Override
  public String toString() {
    return Objects.toString(value, "<null>");
  }

  @Override
  public <R, V extends InitialVisitor<R>> R visit(V visitor) {
    return visitor.visitLiteral(this);
  }
}
