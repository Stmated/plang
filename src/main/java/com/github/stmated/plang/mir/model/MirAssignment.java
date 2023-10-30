package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.mir.MirIdentifierId;

public record MirAssignment(MirIdentifierId iid, MirOperand value) implements MirInstruction, MirOperand {

  @Override
  public String toString() {
    return STR."\{iid} = \{value}";
  }

  @Override
  public String toShortString() {
    return this.iid.name();
  }
}
