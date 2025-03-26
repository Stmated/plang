package org.inf.mir.model;

import org.inf.mir.Mir.Instr;

public record MirFnArgument(
  String name,
  Instr instruction
) {

  @Override
  public String toString() {
    return name + " of " + instruction;
  }
}
