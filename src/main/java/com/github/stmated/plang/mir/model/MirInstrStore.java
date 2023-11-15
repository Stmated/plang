package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.mir.MirIdentifierId;
import com.github.stmated.plang.ty.Ty;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrStore extends AbstractMirInstr {

//  MirIdentifierId name;
  MirInstrStore target;
  MirInstr value;
  Ty ty;

  @Override
  public String toString() {
    return STR."Store (\{value.toShortString()})";
  }
}
