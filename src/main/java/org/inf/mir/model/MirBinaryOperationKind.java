package org.inf.mir.model;

import org.inf.hir.Hir.BinaryOperationKind;

public enum MirBinaryOperationKind {

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
  NOT_EQUALS,
  IS,

  OR,
  AND,

  BIT_OR,
  BIT_AND;

  public boolean isPredicate() {
    return this == LTE || this == GTE || this == LT || this == GT || this == EQUALS || this == NOT_EQUALS || this == IS || this == OR || this == AND;
  }

  public static MirBinaryOperationKind ofHirKind(BinaryOperationKind kind) {

    return switch (kind) {
      case ADD -> MirBinaryOperationKind.ADD;
      case SUBTRACT -> MirBinaryOperationKind.SUBTRACT;
      case MULTIPLY -> MirBinaryOperationKind.MULTIPLY;
      case DIVIDE -> MirBinaryOperationKind.DIVIDE;
      case MODULUS -> MirBinaryOperationKind.MODULUS;
      case REMAINDER -> MirBinaryOperationKind.REMAINDER;
      case POW -> MirBinaryOperationKind.POW;
      case BIT_SHIFT_LEFT -> MirBinaryOperationKind.BIT_SHIFT_LEFT;
      case BIT_SHIFT_RIGHT -> MirBinaryOperationKind.BIT_SHIFT_RIGHT;
      case LTE -> MirBinaryOperationKind.LTE;
      case GTE -> MirBinaryOperationKind.GTE;
      case LT -> MirBinaryOperationKind.LT;
      case GT -> MirBinaryOperationKind.GT;
      case EQUALS -> MirBinaryOperationKind.EQUALS;
      case NOT_EQUALS -> MirBinaryOperationKind.NOT_EQUALS;
      case IS -> MirBinaryOperationKind.IS;
      case OR -> MirBinaryOperationKind.OR;
      case AND -> MirBinaryOperationKind.AND;
      case BIT_OR -> MirBinaryOperationKind.BIT_OR;
      case BIT_AND -> MirBinaryOperationKind.BIT_AND;
    };
  }
}
