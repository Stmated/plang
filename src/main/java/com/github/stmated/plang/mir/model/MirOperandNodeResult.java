package com.github.stmated.plang.mir.model;

public record MirOperandNodeResult(MirNode node) implements MirOperand {

  @Override
  public String toString() {
    return STR."{Node \{node}}";
  }
}
