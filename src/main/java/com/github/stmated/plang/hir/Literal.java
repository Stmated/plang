package com.github.stmated.plang.hir;

public record Literal(Object literal) implements Expression {

  @Override
  public Type getResultType() {
    return Type.fromJavaType(this.literal());
  }
}
