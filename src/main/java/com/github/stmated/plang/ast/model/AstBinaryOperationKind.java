package com.github.stmated.plang.ast.model;

import com.github.stmated.plang.lexer.TokenType;

public enum AstBinaryOperationKind {

  LTE,
  GTE,
  EQUALS,
  NOT_EQUALS,
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

  DIVIDE_ASSIGNMENT,
  MULTIPLY_ASSIGNMENT,
  ADDITION_ASSIGNMENT,
  SUBTRACTION_ASSIGNMENT,

  OR,
  AND,

  BIT_OR,
  BIT_AND;

  public static AstBinaryOperationKind fromTokenType(TokenType tokenType) {

    return switch (tokenType) {
      case LTE -> AstBinaryOperationKind.LTE;
      case GTE -> AstBinaryOperationKind.GTE;
      case EQUALS -> AstBinaryOperationKind.EQUALS;
      case NOT_EQUALS -> AstBinaryOperationKind.NOT_EQUALS;
      case LT -> AstBinaryOperationKind.LT;
      case GT -> AstBinaryOperationKind.GT;
      case IS -> AstBinaryOperationKind.IS;

      case ADD -> AstBinaryOperationKind.ADD;
      case SUBTRACT -> AstBinaryOperationKind.SUBTRACT;
      case MULTIPLY -> AstBinaryOperationKind.MULTIPLY;
      case DIVIDE -> AstBinaryOperationKind.DIVIDE;
      case MODULUS -> AstBinaryOperationKind.MODULUS;
      case REMAINDER -> AstBinaryOperationKind.REMAINDER;
      case POW -> AstBinaryOperationKind.POW;

      case ADDITION_ASSIGNMENT -> AstBinaryOperationKind.ADDITION_ASSIGNMENT;
      case SUBTRACTION_ASSIGNMENT -> AstBinaryOperationKind.SUBTRACTION_ASSIGNMENT;
      case MULTIPLY_ASSIGNMENT -> AstBinaryOperationKind.MULTIPLY_ASSIGNMENT;
      case DIVIDE_ASSIGNMENT -> AstBinaryOperationKind.DIVIDE_ASSIGNMENT;

      case BIT_SHIFT_LEFT -> AstBinaryOperationKind.BIT_SHIFT_LEFT;
      case BIT_SHIFT_RIGHT -> AstBinaryOperationKind.BIT_SHIFT_RIGHT;

      case OR -> AstBinaryOperationKind.OR;
      case AND -> AstBinaryOperationKind.AND;

      case BIT_OR -> AstBinaryOperationKind.BIT_OR;
      case BIT_AND -> AstBinaryOperationKind.BIT_AND;

      default -> throw new IllegalArgumentException("TokenType '%s' is not a binary operator");
    };
  }
}
