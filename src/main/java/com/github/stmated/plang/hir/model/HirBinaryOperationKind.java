package com.github.stmated.plang.hir.model;

public enum HirBinaryOperationKind {

  ADD,
  SUBTRACT,
  MULTIPLY,
  DIVIDE,
  MODULUS,
  REMAINDER,
  POW,

  BIT_SHIFT_LEFT,
  BIT_SHIFT_RIGHT,

  LTE,
  GTE,

  LT,
  GT,

  EQUALS,
  IS,

  OR,
  AND,

  BIT_OR,
  BIT_AND;

  public boolean isPredicate() {
    return this == LTE || this == GTE || this == LT || this == GT || this == EQUALS || this == IS || this == OR || this == AND;
  }
}
