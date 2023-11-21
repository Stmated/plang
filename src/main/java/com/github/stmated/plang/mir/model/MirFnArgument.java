package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.mir.Mir.Instr;

public record MirFnArgument(
  String name,
  Instr instruction
) {

}
