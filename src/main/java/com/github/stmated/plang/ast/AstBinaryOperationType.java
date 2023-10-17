package com.github.stmated.plang.ast;

import com.github.stmated.plang.parser.TokenType;

public enum AstBinaryOperationType {

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

  public static AstBinaryOperationType fromTokenType(TokenType tokenType) {

    return switch (tokenType) {
      case LTE -> AstBinaryOperationType.LTE;
      case GTE -> AstBinaryOperationType.GTE;
      case EQUALS -> AstBinaryOperationType.Equals;
      case LT -> AstBinaryOperationType.LT;
      case GT -> AstBinaryOperationType.GT;
      case IS -> AstBinaryOperationType.IS;

      case PLUS -> AstBinaryOperationType.ADD;
      case MINUS -> AstBinaryOperationType.SUBTRACT;
      case MULTIPLY -> AstBinaryOperationType.MULTIPLY;
      case DIVIDE -> AstBinaryOperationType.DIVIDE;
      case MODULUS -> AstBinaryOperationType.MODULUS;
      case REMAINDER -> AstBinaryOperationType.REMAINDER;
      case POW -> AstBinaryOperationType.POW;

      case BIT_SHIFT_LEFT -> AstBinaryOperationType.BIT_SHIFT_LEFT;
      case BIT_SHIFT_RIGHT -> AstBinaryOperationType.BIT_SHIFT_RIGHT;

      case OR -> AstBinaryOperationType.OR;
      case AND -> AstBinaryOperationType.AND;

      case BIT_OR -> AstBinaryOperationType.BIT_OR;
      case BIT_AND -> AstBinaryOperationType.BIT_AND;

      default -> throw new IllegalArgumentException("TokenType '%s' is not a binary operator");
    };
  }
}
