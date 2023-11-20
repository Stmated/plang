package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.TyStruct;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrCreateStruct extends AbstractMirInstr {

  TyStruct ty;

  @Override
  public String toString() {
    return Objects.toString(ty);
  }
}
