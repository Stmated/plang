package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;
import java.util.Arrays;
import java.util.stream.Collectors;

public record AstBracket(AstExpression[] children) implements AstExpression {

  @Override
  public String toString() {

    final var childrenString = Arrays.stream(children).map(Object::toString).collect(Collectors.joining(", "));
    return STR."[\{childrenString}]";
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitBracket(this);
  }
}
