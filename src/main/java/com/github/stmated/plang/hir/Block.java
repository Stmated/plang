package com.github.stmated.plang.hir;

public record Block(Expression[] children) implements Expression {

  @Override
  public Type getResultType() {

    // TODO: Needs to be dealt with in some other way. Maybe give back a composition type that is compressed later?
    if (children.length > 0) {
      return children[0].getResultType();
    }

    return null;
  }
}
