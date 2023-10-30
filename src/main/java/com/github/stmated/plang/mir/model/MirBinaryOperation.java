package com.github.stmated.plang.mir.model;

public record MirBinaryOperation(MirOperand lhs, MirBinaryOperationKind kind, MirOperand rhs) implements MirInstruction, MirOperand {

  @Override
  public String toString() {
    return STR."\{lhs.toShortString()} \{kind} \{rhs.toShortString()}";
  }
}
