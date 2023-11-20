package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.ast.AstVisitor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public record AstExpressions(
  AstExpression[] children
) implements AstExpression {

  @Override
  public String toString() {
    return Arrays.toString(children);
  }

  @Override
  public <R, V extends AstVisitor<R>> R visit(V visitor) {
    return visitor.visitExpressionCollection(this);
  }

  public static AstExpression from(List<AstExpression> collection) {

    if (collection.isEmpty()) {
      return null;
    }

    if (collection.size() == 1) {
      return collection.get(0);
    } else {
      return new AstExpressions(collection.toArray(new AstExpression[0]));
    }
  }
}
