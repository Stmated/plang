package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrGetParam extends AbstractMirInstr {

  MirFnParameter parameter;

  @Override
  public Ty ty() {
    return parameter.ty();
  }

  @Override
  public String toString() {
    return STR."param:\{parameter.name()}";
  }
}
