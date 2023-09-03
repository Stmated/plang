package com.github.stmated.plang.ipr;

public record InitialBinaryOperation(InitialExpression lhs, InitialBinaryOperationType type, InitialExpression rhs) implements InitialExpression {
}
