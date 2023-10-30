package com.github.stmated.plang.mir.model;

public record MirInstrConditionalJump(MirOperand predicate, MirNode pass, MirNode fail) implements MirInstruction {

  @Override
  public String toString() {
    return STR."if \{predicate} then \{pass.name()} else \{fail.name()}";
  }
}
