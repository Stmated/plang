package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrGetArrayElement extends AbstractMirInstr {

  MirInstr target;
  MirInstr accessor;
  Ty ty;

  @Override
  public String toString() {
    return STR."\{target()}[\{accessor}]";
  }
}
