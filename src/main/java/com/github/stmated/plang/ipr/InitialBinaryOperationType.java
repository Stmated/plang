package com.github.stmated.plang.ipr;

import com.github.stmated.plang.parser.TokenType;

public enum InitialBinaryOperationType {

  LTE,
  GTE,
  Equals,
  LT,
  GT,
  IS,


  ADD,
  SUBTRACT,
  MULTIPLY,
  DIVIDE,
  MODULUS,
  REMAINDER,
  POW,
  BIT_SHIFT_LEFT,
  BIT_SHIFT_RIGHT,

  OR,
  AND,

  BIT_OR,
  BIT_AND;

  public static InitialBinaryOperationType fromTokenType(TokenType tokenType) {

    return switch (tokenType) {
      case LTE -> InitialBinaryOperationType.LTE;
      case GTE -> InitialBinaryOperationType.GTE;
      case EQUALS -> InitialBinaryOperationType.Equals;
      case LT -> InitialBinaryOperationType.LT;
      case GT -> InitialBinaryOperationType.GT;
      case IS -> InitialBinaryOperationType.IS;

      case PLUS -> InitialBinaryOperationType.ADD;
      case MINUS -> InitialBinaryOperationType.SUBTRACT;
      case MULTIPLY -> InitialBinaryOperationType.MULTIPLY;
      case DIVIDE -> InitialBinaryOperationType.DIVIDE;
      case MODULUS -> InitialBinaryOperationType.MODULUS;
      case REMAINDER -> InitialBinaryOperationType.REMAINDER;
      case POW -> InitialBinaryOperationType.POW;

      case BIT_SHIFT_LEFT -> InitialBinaryOperationType.BIT_SHIFT_LEFT;
      case BIT_SHIFT_RIGHT -> InitialBinaryOperationType.BIT_SHIFT_RIGHT;

      case OR -> InitialBinaryOperationType.OR;
      case AND -> InitialBinaryOperationType.AND;

      case BIT_OR -> InitialBinaryOperationType.BIT_OR;
      case BIT_AND -> InitialBinaryOperationType.BIT_AND;

      default -> throw new IllegalArgumentException("TokenType '%s' is not a binary operator");
    };
  }
}
