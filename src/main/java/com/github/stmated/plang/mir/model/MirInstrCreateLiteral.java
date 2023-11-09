package com.github.stmated.plang.mir.model;

import com.github.stmated.plang.ty.Ty;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MirInstrCreateLiteral extends AbstractMirInstr {

  String content;
  Ty ty;

  @Override
  public String toString() {
    return STR."\{content}:\{ty.toShortString()}";
  }
}
