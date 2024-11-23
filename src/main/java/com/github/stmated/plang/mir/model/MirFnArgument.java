package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.mir.Mir.Instr;

public record MirFnArgument(
  String name,
  Instr instruction
) {

  @Override
  public String toString() {
    return name + " of " + instruction;
  }
}
