package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirCall extends MirInstr {

  MirFn target;
  MirFnArgument[] arguments;
  Ty returnType;

  @Override
  public Ty ty() {
    return returnType;
  }
}
