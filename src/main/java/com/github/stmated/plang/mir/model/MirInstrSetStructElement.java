package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrSetStructElement extends AbstractMirInstr {

  MirInstr target;
  int index;
  MirInstr value;
  Ty ty;

  @Override
  public String toString() {
    return STR."\{target()}[\{index}] = \{value}";
  }
}
