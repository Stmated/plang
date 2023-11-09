package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.mir.MirIdentifierId;
import com.github.stmated.plang.ty.Ty;
import lombok.Value;

@Value
//@EqualsAndHashCode(callSuper = true)
public class MirInstrStore implements MirInstr {

  MirIdentifierId name;
  MirInstr value;
  Ty ty;

  @Override
  public String toString() {
    return STR."\{value.toShortString()}*";
  }
}
