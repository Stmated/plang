package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrGetGlobal extends AbstractMirInstr {

  String globalName;
  Ty ty;

  @Override
  public String toString() {
    return globalName;
  }
}
