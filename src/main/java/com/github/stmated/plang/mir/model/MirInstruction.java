package com.github.stmated.plang.mir.model;

public interface MirInstruction {

  default boolean isTerminal() {
    return false;
  }
}
