package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrConditionalJump extends MirInstr {

  MirInstr predicate;
  MirNode pass;
  MirNode fail;

  @Override
  public String toString() {
    return STR."if \{predicate} then \{pass.name()} else \{fail.name()}";
  }

  @Override
  public Ty ty() {
    return Ty.VOID;
  }
}
